// ==UserScript==
// @name         ResourceTree - Bilibili 收藏夹导出
// @namespace    ResourceTree
// @version      0.1.2
// @description  通过只读 API 完整分页导出自己创建的收藏夹为 ResourceTree JSON
// @match        https://space.bilibili.com/*
// @grant        GM_xmlhttpRequest
// @connect      api.bilibili.com
// @run-at       document-idle
// @noframes
// ==/UserScript==

/* Architecture: 1. API Layer  2. ResourceTree Converter  3. Exporter
 *               4. UI         5. Bootstrap
 * Schema authority: app/src/main/java/com/example/resouretree/data/transfer/TreeJson.kt
 * No page-card scraping, credential storage, external dependencies or write APIs.
 */
(function () {
    'use strict';

    const PAGE_SIZE = 20;
    const MAX_PAGES_PER_FOLDER = 10000;
    const MAX_NODES = 10000;
    const MAX_BYTES = 10 * 1024 * 1024;
    const DEFAULT_PACKAGE = 'tv.danmaku.bili';
    const LOGIN_MESSAGE = '尚未登录 Bilibili。\n请先在当前浏览器登录 Bilibili，然后重新导出。';
    const sleep = ms => new Promise(resolve => setTimeout(resolve, ms));
    const isObject = value => value !== null && typeof value === 'object' && !Array.isArray(value);
    const countValue = value => Number.isSafeInteger(value) && value >= 0 ? value : null;
    const cleanText = value => typeof value === 'string' ? value.trim() : '';
    function checkCancelled(state) {
        if (state.cancelRequested) throw new CancelledError();
    }
    class CancelledError extends Error {
        constructor() { super('导出已取消'); this.name = 'CancelledError'; }
    }
    class RequestError extends Error {
        constructor(message, retryable = false) { super(message); this.retryable = retryable; }
    }
    function positiveId(value) {
        if (typeof value === 'number' && (!Number.isSafeInteger(value) || value <= 0)) return null;
        return /^(?:[1-9]\d*)$/.test(String(value)) ? String(value) : null;
    }

    // 1. API Layer: the manager attaches the existing browser session itself.
    function gmRequest(url) {
        return new Promise((resolve, reject) => {
            if (typeof GM_xmlhttpRequest !== 'function') {
                reject(new RequestError('缺少 GM_xmlhttpRequest，请在 Tampermonkey / Violentmonkey 中安装并启用脚本。'));
                return;
            }
            GM_xmlhttpRequest({
                method: 'GET', url, anonymous: false, timeout: 30000,
                headers: { Accept: 'application/json' },
                onload: resolve,
                onerror: () => reject(new RequestError('临时网络错误，无法连接 Bilibili API。', true)),
                ontimeout: () => reject(new RequestError('Bilibili API 请求超时。', true)),
                onabort: () => reject(new RequestError('请求被中止。')),
            });
        });
    }

    class BiliApi {
        constructor(state, { transport = gmRequest, wait = sleep, onRetry = () => {} } = {}) {
            this.state = state;
            this.transport = transport;
            this.wait = wait;
            this.onRetry = onRetry;
        }
        async apiRequest(path, params, context) {
            const url = new URL(path, 'https://api.bilibili.com');
            for (const [key, value] of Object.entries(params)) url.searchParams.set(key, String(value));
            for (let attempt = 0; attempt <= 3; attempt++) {
                checkCancelled(this.state);
                await this.wait(attempt === 0 ? 200 : 500 * 2 ** (attempt - 1));
                checkCancelled(this.state);
                try {
                    const response = await this.transport(url.href);
                    checkCancelled(this.state);
                    if (response.finalUrl && new URL(response.finalUrl).origin !== url.origin) {
                        throw new RequestError('API 重定向到其他站点，请检查登录状态或稍后重试。');
                    }
                    const status = response.status;
                    if (status === 412 || status === 403) {
                        throw new RequestError(`HTTP ${status}：访问被限制或需要验证，请稍后在 Bilibili 网页检查。不会绕过限制。`);
                    }
                    if (status === 401) throw new RequestError(LOGIN_MESSAGE);
                    if (status !== 200) throw new RequestError(`HTTP ${status}：请求失败。`, status === 0 || status === 429 || status >= 500 && status <= 599);
                    let envelope;
                    try { envelope = JSON.parse(response.responseText); }
                    catch { throw new RequestError('API 未返回有效 JSON，可能需要登录、验证或接口已变更。'); }
                    if (!isObject(envelope) || !Number.isInteger(envelope.code)) throw new RequestError('API 返回结构异常：缺少数字 code。');
                    if (envelope.code !== 0) {
                        const message = cleanText(envelope.message).slice(0, 240) || '无错误说明';
                        throw new RequestError(`Bilibili API: code = ${envelope.code}\nmessage = ${message}\n${envelope.code === -101 ? LOGIN_MESSAGE : '请检查网页登录状态、访问权限或稍后重试。'}`);
                    }
                    if (!isObject(envelope.data)) throw new RequestError('API 返回结构异常：data 必须为对象。');
                    return envelope.data;
                } catch (error) {
                    checkCancelled(this.state);
                    if (error.retryable && attempt < 3) {
                        this.onRetry(`${context}：${error.message}\n准备第 ${attempt + 1} / 3 次重试…`);
                    } else {
                        throw new Error(`${context}失败\n${error.message}`);
                    }
                }
            }
        }
        async getCurrentUser() {
            const data = await this.apiRequest('/x/web-interface/nav', {}, '读取当前账号');
            if (data.isLogin === false) throw new Error(LOGIN_MESSAGE);
            if (data.isLogin !== true || !positiveId(data.mid)) throw new Error('登录接口返回结构异常：缺少 isLogin / mid。');
            return { mid: positiveId(data.mid), name: cleanText(data.uname) };
        }
        async getCreatedFolders(mid) {
            const data = await this.apiRequest('/x/v3/fav/folder/created/list-all', { up_mid: mid }, '读取自己创建的收藏夹');
            const total = countValue(data.count);
            const list = data.list === null && total === 0 ? [] : data.list;
            if (!Array.isArray(list)) throw new Error('收藏夹列表结构异常：缺少 list 数组。');
            if (total !== null && total !== list.length) throw new Error('收藏夹列表数量不完整，未开始导出。请稍后重试。');
            const ids = new Set();
            return list.map(folder => {
                if (!isObject(folder) || !positiveId(folder.id) || !cleanText(folder.title)) throw new Error('收藏夹结构异常：缺少有效 id / title。');
                const id = positiveId(folder.id);
                if (ids.has(id)) throw new Error('API 返回重复收藏夹，未开始导出。');
                ids.add(id);
                return { id, title: folder.title.trim(), mediaCount: countValue(folder.media_count) };
            });
        }
        getFolderMediaPage(folder, page) {
            return this.apiRequest('/x/v3/fav/resource/list', {
                media_id: folder.id, pn: page, ps: PAGE_SIZE, platform: 'web',
            }, `读取收藏夹“${folder.title}”第 ${page} 页`);
        }
    }

    // 2. ResourceTree Converter: only fields supported by the actual TreeJson.
    function uuid(cryptoSource = globalThis.crypto) {
        if (typeof cryptoSource?.randomUUID === 'function') return cryptoSource.randomUUID();
        if (typeof cryptoSource?.getRandomValues !== 'function') throw new Error('浏览器不支持安全随机 ID，请使用新版浏览器。');
        const bytes = cryptoSource.getRandomValues(new Uint8Array(16));
        bytes[6] = (bytes[6] & 15) | 64;
        bytes[8] = (bytes[8] & 63) | 128;
        const hex = Array.from(bytes, b => b.toString(16).padStart(2, '0')).join('');
        return `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20)}`;
    }
    function validBvid(value) {
        const bv = cleanText(value);
        return bv.startsWith('BV') && bv.length > 2 && !/\s/.test(bv) ? bv : '';
    }
    function baseNode(type, name, sortOrder, now, newId) {
        return { id: newId(), type, name, sortOrder, createdAt: now, updatedAt: now };
    }
    function convertFolder(folder, children, index, now, newId = uuid) {
        return { ...baseNode('folder', folder.title, index, now, newId), children };
    }
    function convertMedia(media, index, now, packageName, newId = uuid) {
        if (!isObject(media)) return { reason: '资源结构异常' };
        if (media.type != null && media.type !== 2 && media.type !== '2') return { reason: '非视频资源' };
        const bvid = validBvid(media.bvid) || validBvid(media.bv_id);
        if (!bvid) return { reason: '缺少有效 BV' };
        const title = cleanText(media.title);
        if (!title) return { reason: '缺少视频标题' };
        return { node: {
            ...baseNode('item', title, index, now, newId), content: bvid, tags: [],
            action: { type: 'COPY_AND_LAUNCH', text: bvid, packageName },
        } };
    }
    function serializeExport(file) {
        let count = 0;
        const visit = node => { count++; (node.children || []).forEach(visit); };
        file.roots.forEach(visit);
        if (count > MAX_NODES) throw new Error('超过 ResourceTree 一次最多 10000 个节点的限制，请减少所选收藏夹。');
        const text = JSON.stringify(file, null, 2);
        if (new Blob([text]).size > MAX_BYTES) throw new Error('超过 ResourceTree 的 10 MB 文件限制，请减少所选收藏夹。');
        return text;
    }

    function selectVideos(file, selectedIds) {
        const root = file.roots[0];
        const children = root.children.map(folder => ({
            ...folder, children: folder.children.filter(item => selectedIds.has(item.id)),
        })).filter(folder => folder.children.length > 0);
        if (!children.length) throw new Error('请至少勾选一个视频。');
        return { schemaVersion: file.schemaVersion, roots: [{ ...root, children }] };
    }

    // 3. Exporter: sequential reads; a folder error never produces a partial file.
    class Exporter {
        constructor(api, state, onProgress = () => {}, { now = Date.now(), newId = uuid, maxPages = MAX_PAGES_PER_FOLDER } = {}) {
            this.api = api; this.state = state; this.onProgress = onProgress;
            this.now = now; this.newId = newId; this.maxPages = maxPages;
            this.stats = { completed: 0, total: 0, folder: '', page: 0, read: 0, items: 0, skipped: 0, reasons: {}, records: [], countWarnings: [] };
        }
        progress() { this.onProgress(this.stats); }
        async fetchFolder(folder, index, packageName) {
            const children = [];
            const seenResources = new Set();
            let read = 0;
            let expected = countValue(folder.mediaCount);
            let snapshotCount = null;
            for (let page = 1; page <= this.maxPages; page++) {
                checkCancelled(this.state);
                this.stats.page = page;
                this.progress();
                const context = `收藏夹“${folder.title}”第 ${page} 页`;
                const fail = message => { throw new Error(`${context}：${message}`); };
                const data = await this.api.getFolderMediaPage(folder, page);
                checkCancelled(this.state);
                if (!isObject(data)) fail('返回结构异常。');
                const currentCount = countValue(data.info?.media_count);
                const more = data.has_more === true || data.has_more === 1 ? true
                    : data.has_more === false || data.has_more === 0 ? false : null;
                if (data.has_more != null && more === null) fail('has_more 类型异常。');
                const medias = data.medias === null && more === false ? [] : data.medias;
                if (!Array.isArray(medias)) fail('缺少 medias 数组，不能确认结果完整。');
                if (medias.length === 0) {
                    if (more === true) fail('has_more=true 却返回空页，不能确认分页结束。');
                    // Out-of-range pages may return a default/empty info object
                    // (including media_count:0). It is not a new folder snapshot.
                    // Only a first empty response can supply a previously unknown
                    // positive count. Never erase a known gap with a terminal zero.
                    if (read === 0 && expected === null && currentCount > 0) expected = currentCount;
                    if (expected !== null && read < expected) {
                        // media_count is a displayed total, not proof that every
                        // counted entry is available through this endpoint. Confirm
                        // exhaustion with an empty page and require an explicit
                        // download decision; never label the difference as deleted.
                        this.stats.countWarnings.push({ folder: folder.title, expected, read, missing: expected - read });
                    }
                    return convertFolder(folder, children, index, this.now, this.newId);
                }
                if (currentCount !== null) {
                    if (snapshotCount !== null && currentCount !== snapshotCount) {
                        fail(`接口在非空页返回了不同总数（${snapshotCount} → ${currentCount}），无法确认分页一致性；原因未确定，请稍后重试。`);
                    }
                    snapshotCount = currentCount;
                    expected = currentCount;
                }
                for (const media of medias) {
                    // No global de-duplication. Repeated membership within one folder
                    // indicates shifted/repeated API pages; fail rather than lose data.
                    const key = isObject(media) ? (positiveId(media.id)
                        ? `${media.type ?? 2}:${media.id}` : validBvid(media.bvid) || validBvid(media.bv_id)) : '';
                    if (key && seenResources.has(key)) fail('出现重复资源，分页可能重复或收藏夹正在变化，请重试。');
                    if (key) seenResources.add(key);
                    read++; this.stats.read++;
                    const result = convertMedia(media, children.length, this.now, packageName, this.newId);
                    if (result.node) { children.push(result.node); this.stats.items++; }
                    else {
                        this.stats.skipped++;
                        this.stats.reasons[result.reason] = (this.stats.reasons[result.reason] || 0) + 1;
                        if (this.stats.records.length < 100) this.stats.records.push(`${folder.title}：${cleanText(media?.title).slice(0, 160) || '未命名资源'} — ${result.reason}`);
                    }
                    if (1 + this.stats.total + this.stats.items > MAX_NODES) fail('超过 ResourceTree 的 10000 节点限制，请减少所选收藏夹。');
                }
                this.progress();
                if (more === false) {
                    if (expected === null || read >= expected) return convertFolder(folder, children, index, this.now, this.newId);
                    // A terminal flag with a count gap needs a next-page probe.
                    // If more resources are returned, process them normally.
                    this.stats.pageHint = `接口标注 ${expected} 项，已返回 ${read} 项，正在检查下一页…`;
                    this.progress();
                    continue;
                }
                // Explicit true wins over short pages/stale counts. If the flag is
                // absent, both count and short-page hints still need an empty-page
                // probe; neither alone is safe evidence that all data was read.
                if (more === null && (medias.length < PAGE_SIZE || expected !== null && read >= expected)) {
                    this.stats.pageHint = '正在确认是否还有下一页';
                } else this.stats.pageHint = '';
            }
            throw new Error(`收藏夹“${folder.title}”：达到 ${this.maxPages} 页安全上限，未生成文件。`);
        }
        async run(folders, packageName = DEFAULT_PACKAGE) {
            if (!folders.length) throw new Error('请至少选择一个收藏夹。');
            if (!cleanText(packageName)) throw new Error('目标 package 不能为空。');
            this.stats.total = folders.length;
            if (1 + folders.length > MAX_NODES) throw new Error('收藏夹数量超过 ResourceTree 节点上限。');
            const children = [];
            for (const folder of folders) {
                checkCancelled(this.state);
                this.stats.folder = folder.title; this.stats.page = 0; this.stats.pageHint = '';
                this.progress();
                children.push(await this.fetchFolder(folder, children.length, packageName.trim()));
                this.stats.completed++; this.progress();
            }
            checkCancelled(this.state);
            const file = { schemaVersion: 1, roots: [convertFolder({ title: '哔哩哔哩' }, children, 0, this.now, this.newId)] };
            return { file, text: serializeExport(file), stats: this.stats };
        }
    }
    function filename(date = new Date()) {
        const localDate = [date.getFullYear(), String(date.getMonth() + 1).padStart(2, '0'), String(date.getDate()).padStart(2, '0')].join('-');
        return `resourcetree-bilibili-${localDate}.json`;
    }
    function downloadJson(text, name) {
        // Bilibili can intercept/rewrite clicks on anchors in the host document,
        // even before an anchor's own listener runs. A separate document isolates
        // both the anchor and its click event from host routing/tracking handlers.
        // No scripts, external URL, popups or top-level navigation are permitted.
        const frame = document.createElement('iframe');
        frame.hidden = true;
        frame.title = 'ResourceTree 本地文件下载';
        frame.setAttribute('aria-hidden', 'true');
        frame.setAttribute('sandbox', 'allow-same-origin allow-downloads');
        document.body.append(frame);
        let url;
        try {
            const isolatedDocument = frame.contentDocument;
            if (!isolatedDocument?.body) throw new Error('无法建立本地下载框架，请刷新后重试。');
            url = URL.createObjectURL(new Blob([text], { type: 'application/json;charset=utf-8' }));
            const anchor = isolatedDocument.createElement('a');
            anchor.href = url; anchor.download = name;
            isolatedDocument.body.append(anchor);
            anchor.click();
        } catch (error) {
            if (url) URL.revokeObjectURL(url);
            frame.remove();
            throw error;
        }
        // Keep the source document and Blob alive until the browser starts saving.
        setTimeout(() => { URL.revokeObjectURL(url); frame.remove(); }, 60000);
    }

    // 4. UI: external strings are always textContent, never HTML.
    function element(tag, text, className) {
        const node = document.createElement(tag);
        if (text !== undefined) node.textContent = text;
        if (className) node.className = className;
        return node;
    }
    function button(text, action) {
        const node = element('button', text);
        node.type = 'button'; node.addEventListener('click', action);
        return node;
    }
    class ExportPanel {
        constructor() {
            this.busy = false; this.state = null; this.folders = []; this.user = null;
            this.panel = element('section'); this.panel.id = 'rt-export-panel'; this.panel.hidden = true;
            this.panel.setAttribute('role', 'dialog'); this.panel.setAttribute('aria-label', '导出到 ResourceTree');
            const heading = element('h2', '导出到 ResourceTree');
            this.close = button('关闭', () => { if (!this.busy) this.panel.hidden = true; });
            this.status = element('p', ''); this.status.setAttribute('role', 'status');
            this.progressBar = element('progress'); this.progressBar.max = 1; this.progressBar.value = 0; this.progressBar.hidden = true;
            this.content = element('div'); this.content.className = 'rt-export-content';
            this.controls = element('div', undefined, 'rt-export-controls');
            this.reload = button('重新读取收藏夹', () => this.load());
            this.start = button('开始导出', () => this.startExport()); this.start.disabled = true;
            this.cancel = button('取消', () => {
                if (this.state) this.state.cancelRequested = true;
                this.cancel.disabled = true; this.status.textContent += '\n正在取消，等待当前请求结束…';
            });
            this.cancel.hidden = true;
            this.controls.append(this.reload, this.start, this.cancel);
            this.panel.append(heading, this.close, this.status, this.progressBar, this.content, this.controls);
            document.body.append(this.panel);
        }
        async open() { this.panel.hidden = false; if (!this.busy && !this.user) await this.load(); }
        setBusy(value) {
            this.busy = value; this.close.disabled = value; this.reload.disabled = value;
            this.start.disabled = value || !this.folders.length;
            if (this.selection) this.selection.disabled = value;
            this.cancel.hidden = !value; this.cancel.disabled = false;
        }
        makeApi() {
            return new BiliApi(this.state, { onRetry: message => { this.status.textContent += `\n${message}`; } });
        }
        showError(error) {
            this.status.textContent = error instanceof CancelledError ? '导出已取消\n没有生成 JSON。'
                : `导出失败\n${error.message}\n没有生成不完整的 JSON。`;
        }
        async load() {
            if (this.busy) return;
            this.state = { cancelRequested: false }; this.setBusy(true);
            this.content.replaceChildren(); this.folders = []; this.user = null; this.progressBar.hidden = true;
            this.status.textContent = '正在识别当前登录账号并读取收藏夹…';
            try {
                const api = this.makeApi();
                this.user = await api.getCurrentUser();
                this.folders = await api.getCreatedFolders(this.user.mid);
                checkCancelled(this.state);
                this.renderSelection();
                this.status.textContent = this.folders.length ? `当前账号：${this.user.name || '已登录'}\n请选择要导出的收藏夹（默认全选）。` : '当前账号没有自己创建的收藏夹。';
            } catch (error) { this.user = null; this.showError(error); }
            finally { this.setBusy(false); }
        }
        renderSelection() {
            this.selection = element('fieldset'); this.selection.append(element('legend', '自己创建的视频收藏夹'));
            this.checkboxes = this.folders.map(folder => {
                const label = element('label', undefined, 'rt-export-folder');
                const input = element('input'); input.type = 'checkbox'; input.checked = true;
                label.append(input, element('span', `${folder.title}（${folder.mediaCount ?? '数量未知'}）`));
                this.selection.append(label); return input;
            });
            const all = button('全选', () => this.checkboxes.forEach(input => { input.checked = true; }));
            const none = button('全不选', () => this.checkboxes.forEach(input => { input.checked = false; }));
            this.selection.append(all, none, element('p', '目标 App：Bilibili 国内版'));
            const modeLabel = element('label', undefined, 'rt-export-folder');
            this.pickVideos = element('input'); this.pickVideos.type = 'checkbox';
            modeLabel.append(this.pickVideos, element('span', '先选择视频再导出（可只导出一个）'));
            this.pickVideos.addEventListener('change', () => {
                this.start.textContent = this.pickVideos.checked ? '读取视频供选择' : '开始导出';
            });
            this.start.textContent = '开始导出';
            this.selection.append(modeLabel);
            const advanced = element('details'); advanced.append(element('summary', '高级：目标 package'));
            const label = element('label', '目标 package ');
            this.packageInput = element('input'); this.packageInput.type = 'text'; this.packageInput.value = DEFAULT_PACKAGE;
            label.append(this.packageInput); advanced.append(label, element('p', '例如国际版：com.bilibili.app.in。请确认手机已安装目标 App。'));
            this.selection.append(advanced); this.content.replaceChildren(this.selection);
        }
        renderVideoPicker(result, name) {
            const stats = result.stats;
            const warningText = stats.countWarnings.map(w => `“${w.folder}”：标注 ${w.expected} 项，实际返回 ${w.read} 项，差 ${w.missing} 项（原因未确定）。`).join('\n');
            this.status.textContent = `视频已读取，尚未下载\n可选视频：${stats.items}\n跳过已返回资源：${stats.skipped}\n勾选一个视频即可单独导出。${warningText ? `\n数量差异：\n${warningText}\n接口未返回的资源不在列表中。` : ''}`;
            this.resultView = element('div');
            const picker = element('fieldset'); picker.append(element('legend', '选择要导出的视频'));
            const selectedIds = new Set();
            const selectionCount = element('p', '已选择 0 个视频');
            const download = button('导出选中视频', () => {
                if (this.busy) return;
                try {
                    const file = selectVideos(result.file, selectedIds);
                    downloadJson(serializeExport(file), name);
                    this.status.textContent = `已请求下载选中的 ${selectedIds.size} 个视频\n文件：${name}\n保留“哔哩哔哩 → 收藏夹 → 视频”结构。${warningText ? `\n数量差异仍存在：\n${warningText}\nJSON 仅含勾选视频。` : ''}`;
                } catch (error) { this.showError(error); }
            });
            download.disabled = true;
            const updateSelection = () => {
                selectionCount.textContent = `已选择 ${selectedIds.size} 个视频`;
                download.disabled = selectedIds.size === 0;
            };
            const entries = [];
            const filterLabel = element('label', '按标题或 BV 搜索 ');
            const search = element('input'); search.type = 'text'; filterLabel.append(search);
            const list = element('div', undefined, 'rt-export-video-list');
            for (const folder of result.file.roots[0].children) {
                for (const item of folder.children) {
                    const row = element('label', undefined, 'rt-export-folder');
                    const input = element('input'); input.type = 'checkbox';
                    row.append(input, element('span', `${folder.name} / ${item.name} · ${item.content}`));
                    input.addEventListener('change', () => {
                        if (input.checked) selectedIds.add(item.id); else selectedIds.delete(item.id);
                        updateSelection();
                    });
                    entries.push({ row, input, id: item.id, searchText: `${folder.name} ${item.name} ${item.content}`.toLowerCase() });
                    list.append(row);
                }
            }
            search.addEventListener('input', () => {
                const query = search.value.trim().toLowerCase();
                entries.forEach(entry => { entry.row.hidden = !entry.searchText.includes(query); });
            });
            picker.append(filterLabel, element('p', '默认不勾选；搜索不会清除已选视频。'),
                button('全不选视频', () => {
                    entries.forEach(entry => { entry.input.checked = false; });
                    selectedIds.clear(); updateSelection();
                }), list, selectionCount, download);
            this.resultView.append(picker); this.content.append(this.resultView);
            this.resultView.scrollIntoView({ block: 'nearest' });
        }
        async startExport() {
            if (this.busy || !this.user) return;
            const folders = this.folders.filter((_, i) => this.checkboxes[i].checked);
            const packageName = this.packageInput.value.trim();
            const pickVideos = this.pickVideos.checked;
            if (!folders.length || !packageName) { this.status.textContent = '请至少选择一个收藏夹，并填写目标 package。'; return; }
            this.state = { cancelRequested: false }; this.setBusy(true);
            this.progressBar.hidden = false; this.progressBar.max = folders.length; this.progressBar.value = 0;
            if (this.resultView) this.resultView.remove();
            let result;
            try {
                const api = this.makeApi();
                this.status.textContent = '正在重新确认当前登录账号…';
                const currentUser = await api.getCurrentUser();
                if (currentUser.mid !== this.user.mid) throw new Error('登录账号已切换，请重新读取收藏夹。');
                const exporter = new Exporter(api, this.state, stats => {
                    this.progressBar.value = stats.completed;
                    this.status.textContent = `正在导出 Bilibili 收藏夹\n已完成收藏夹：${stats.completed} / ${stats.total}\n当前：${stats.folder}\n页面：${stats.page}\n已读取资源：${stats.read}\n已获取 Item：${stats.items}\n已跳过：${stats.skipped}${stats.pageHint ? `\n${stats.pageHint}` : ''}`;
                });
                result = await exporter.run(folders, packageName);
                checkCancelled(this.state);
                const name = filename();
                const stats = result.stats;
                if (pickVideos) { this.renderVideoPicker(result, name); return; }
                const hasCountWarnings = stats.countWarnings.length > 0;
                const summary = `收藏夹：${stats.completed}\n视频：${stats.items}\n跳过已返回资源：${stats.skipped}\n文件：${name}`;
                const warningText = stats.countWarnings.map(w => `“${w.folder}”：接口标注 ${w.expected} 项，实际返回 ${w.read} 项，差 ${w.missing} 项。`).join('\n');
                if (!hasCountWarnings) downloadJson(result.text, name);
                this.status.textContent = hasCountWarnings
                    ? `读取结束，存在数量差异\n${summary}\n${warningText}\n已继续分页并确认空页。差额原因无法确定，未计入跳过视频，也无法补入 JSON。\n尚未下载；请核对后决定是否下载已返回的视频。`
                    : `导出完成\n${summary}\n已请求浏览器下载；若未保存，可点击“重新下载”。`;
                this.resultView = element('div');
                this.resultView.append(button(hasCountWarnings ? `确认下载已返回的 ${stats.items} 个视频` : '重新下载', () => {
                    if (this.busy) return;
                    downloadJson(result.text, name);
                    if (hasCountWarnings) this.status.textContent = `已请求下载已返回的资源（存在数量差异）\n${summary}\n${warningText}\nJSON 不包含接口未返回的资源。`;
                }));
                if (stats.skipped) {
                    const details = element('details'); details.append(element('summary', `查看跳过项目（${stats.skipped} 项）`));
                    for (const [reason, count] of Object.entries(stats.reasons)) details.append(element('p', `${reason}：${count} 项`));
                    const list = element('ul'); stats.records.forEach(record => list.append(element('li', record))); details.append(list);
                    if (stats.skipped > stats.records.length) details.append(element('p', '仅显示前 100 条记录，统计包含全部跳过项。'));
                    this.resultView.append(details);
                }
                this.content.append(this.resultView);
            } catch (error) { this.showError(error); }
            finally { this.setBusy(false); }
        }
    }

    // 5. Bootstrap: one persistent space-page button also survives SPA navigation.
    function bootstrap() {
        if (document.getElementById('rt-export-button')) return;
        const style = element('style');
        style.textContent = `
            #rt-export-button { position:fixed; right:20px; bottom:24px; z-index:2147483646; background:#176348; color:white; border:0; border-radius:8px; padding:11px 15px; cursor:pointer; font:14px sans-serif; }
            #rt-export-panel { position:fixed; right:20px; bottom:76px; z-index:2147483647; width:430px; max-width:calc(100vw - 40px); max-height:calc(100vh - 110px); overflow:auto; padding:20px; box-sizing:border-box; background:white; color:#18251e; border:1px solid #ccd8d0; border-radius:12px; box-shadow:0 6px 30px #0003; font:14px/1.6 sans-serif; text-align:left; }
            #rt-export-panel[hidden] { display:none; }
            #rt-export-panel h2 { font-size:19px; margin:0 0 10px; }
            #rt-export-panel p { white-space:pre-wrap; overflow-wrap:anywhere; margin:10px 0; }
            #rt-export-panel button { padding:6px 10px; margin:4px; border:1px solid #b4c5b9; border-radius:5px; background:#f2f7f4; color:#193e2c; cursor:pointer; font:inherit; }
            #rt-export-panel button:disabled { opacity:.5; cursor:default; }
            #rt-export-panel fieldset { min-width:0; padding:10px; border:1px solid #dbe5df; }
            #rt-export-panel .rt-export-folder { display:flex; align-items:baseline; gap:8px; overflow-wrap:anywhere; padding:3px; }
            #rt-export-panel .rt-export-folder[hidden] { display:none; }
            #rt-export-panel .rt-export-video-list { max-height:280px; overflow:auto; margin:8px 0; }
            #rt-export-panel input[type=checkbox] { flex-shrink:0; }
            #rt-export-panel input[type=text] { box-sizing:border-box; max-width:100%; padding:5px; color:#18251e; background:white; border:1px solid #b4c5b9; }
            #rt-export-panel progress { width:100%; accent-color:#176348; }
            #rt-export-panel summary { cursor:pointer; }
            #rt-export-panel li { overflow-wrap:anywhere; margin:6px 0; }
        `;
        document.head.append(style);
        const panel = new ExportPanel();
        const launcher = button('导出 ResourceTree', () => panel.open()); launcher.id = 'rt-export-button';
        document.body.append(launcher);
    }
    // Plain Node can load the same production functions for offline tests.
    // Browser execution never exposes API responses or exporter internals to the page.
    if (typeof document === 'undefined' && typeof module !== 'undefined' && module.exports) {
        module.exports = { BiliApi, Exporter, ExportPanel, bootstrap, gmRequest, convertMedia, convertFolder, serializeExport, selectVideos, uuid, validBvid, filename, CancelledError, MAX_NODES, MAX_BYTES };
    } else bootstrap();
})();
