'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const { readFileSync } = require('node:fs');
const { join } = require('node:path');
const { webcrypto } = require('node:crypto');
const { BiliApi, Exporter, convertMedia, serializeExport, selectVideos, uuid, filename, CancelledError, MAX_BYTES, MAX_NODES, gmRequest } = require('../bilibili-fav-export.user.js');
const { folders, samplePages, video, mockApi, deterministicOptions } = require('./fixtures.cjs');
const { validate } = require('./validate-v2.cjs');
const response = (data, code = 0) => ({ status: 200, responseText: JSON.stringify({ code, message: 'fixture', data }) });
const run = (pages, folder = { id: '1', title: '测试夹', mediaCount: null }, options = {}) =>
    new Exporter(mockApi({ '1': pages }), {}, () => {}, options).run([folder]);

test('shipped sample is produced by the real exporter, with exact schema keys', async () => {
    const result = await new Exporter(mockApi(samplePages), {}, () => {}, deterministicOptions()).run(folders, 'tv.danmaku.bili');
    assert.equal(readFileSync(join(__dirname, '../samples/resourcetree-bilibili.sample.json'), 'utf8'), result.text + '\n');
    assert.deepEqual(Object.keys(result.file), ['schemaVersion', 'roots']);
    const root = result.file.roots[0];
    assert.equal(root.name, '哔哩哔哩');
    assert.deepEqual(Object.keys(root), ['id', 'type', 'name', 'sortOrder', 'createdAt', 'updatedAt', 'children']);
    const item = root.children[0].children[0];
    assert.deepEqual(Object.keys(item), ['id', 'type', 'name', 'sortOrder', 'createdAt', 'updatedAt', 'content', 'tags', 'action']);
    assert.equal(result.file.schemaVersion, 2);
    assert.deepEqual(item.content, { type: 'TEXT', text: 'BVExample1' });
    assert.deepEqual(item.action, { type: 'COPY_AND_LAUNCH', target: 'tv.danmaku.bili' });
    assert.deepEqual(item.tags, []);
});

test('45 resources require three sequential pages and progress counts actual items', async () => {
    const calls = [], progress = [];
    const pages = [20, 20, 5].map((n, p) => ({ info: { media_count: 45 }, has_more: p < 2, medias: Array.from({ length: n }, (_, i) => video(p * 20 + i + 1)) }));
    const result = await new Exporter(mockApi({ '1': pages }, calls), {}, s => progress.push({ ...s })).run([{ id: '1', title: '大收藏夹', mediaCount: 45 }]);
    assert.deepEqual(calls, [['1', 1], ['1', 2], ['1', 3]]);
    assert.equal(result.stats.items, 45);
    assert.equal(progress.at(-1).completed, 1);
});

test('has_more true overrides short page and stale folder-list count', async () => {
    const result = await run([{ has_more: true, medias: [video(1)] }, { has_more: false, medias: [video(2)] }], { id: '1', title: '测试夹', mediaCount: 1 });
    assert.equal(result.stats.items, 2);
});

test('missing has_more probes past short page/count until empty', async () => {
    const result = await run([{ medias: [video(1)] }, { medias: [video(2)] }, { medias: [] }], { id: '1', title: '测试夹', mediaCount: 1 });
    assert.equal(result.stats.items, 2);
});

test('empty folder retained, including medias:null with terminal flag', async () => {
    const result = await run([{ has_more: false, info: { media_count: 0 }, medias: null }]);
    assert.equal(result.file.roots[0].children.length, 1);
    assert.deepEqual(result.file.roots[0].children[0].children, []);
});

test('duplicate video across folders retains independent UUIDs', async () => {
    const page = { has_more: false, medias: [video(1)] };
    const result = await new Exporter(mockApi({ '1': [page], '2': [page] }), {}).run([{ id: '1', title: '甲', mediaCount: 1 }, { id: '2', title: '乙', mediaCount: 1 }]);
    const [a, b] = result.file.roots[0].children.map(f => f.children[0]);
    assert.equal(a.content.text, b.content.text); assert.notEqual(a.id, b.id);
});

test('invalid resources skipped and counted; bv_id fallback and escaped titles survive', async () => {
    const result = await run([{ has_more: false, medias: [
        video(1, { bvid: '' }), video(2, { type: 12 }), video(3, { title: '' }), null,
        video(4, { bvid: 'bad', bv_id: ' BVFutureLong123 ', title: '<img onerror="bad"> 🌲\n标题' }),
    ] }]);
    assert.equal(result.stats.skipped, 4); assert.equal(result.stats.items, 1);
    assert.deepEqual(result.stats.reasons, { '缺少有效 BV': 1, '非视频资源': 1, '缺少视频标题': 1, '资源结构异常': 1 });
    assert.equal(result.file.roots[0].children[0].children[0].content.text, 'BVFutureLong123');
    assert.match(result.text, /onerror/); // Data stays a JSON string, never markup.
});

test('custom target package and UUID fallback', () => {
    const item = convertMedia(video(1), 0, 1, 'com.bilibili.app.in').node;
    assert.equal(item.action.target, 'com.bilibili.app.in');
    const fallback = { getRandomValues: bytes => webcrypto.getRandomValues(bytes) };
    const ids = new Set(Array.from({ length: 100 }, () => uuid(fallback)));
    assert.equal(ids.size, 100);
    for (const id of ids) assert.match(id, /^[\da-f]{8}-[\da-f]{4}-4[\da-f]{3}-[89ab][\da-f]{3}-[\da-f]{12}$/);
});

test('incomplete, contradictory, changed, repeated and malformed pages fail closed', async () => {
    const cases = [
        [{ has_more: true, medias: [] }],
        [{ has_more: true, medias: [video(1)], info: { media_count: 2 } }, { has_more: false, medias: [video(2)], info: { media_count: 3 } }],
        [{ has_more: true, medias: [video(1)] }, { has_more: false, medias: [video(1)] }],
        [{ has_more: false }], [{ has_more: 'false', medias: [] }], [{ medias: null }],
    ];
    for (const pages of cases) await assert.rejects(run(pages), /测试夹.*第 [12] 页/);
});

test('terminal count gap probes next page, then reports unavailable difference separately', async () => {
    for (const total of [15, 56]) {
        const returned = total - 1;
        const pages = [];
        for (let start = 0; start < returned; start += 20) {
            pages.push({ info: { media_count: total }, has_more: start + 20 < returned,
                medias: Array.from({ length: Math.min(20, returned - start) }, (_, i) => video(start + i + 1)) });
        }
        pages.push({ info: { media_count: total }, has_more: false, medias: null });
        const calls = [];
        const result = await new Exporter(mockApi({ '1': pages }, calls), {}).run([{ id: '1', title: '人工数量差异样例', mediaCount: total }]);
        assert.equal(calls.length, Math.ceil(returned / 20) + 1);
        assert.equal(result.stats.items, returned);
        assert.equal(result.stats.skipped, 0);
        assert.deepEqual(result.stats.countWarnings, [{ folder: '人工数量差异样例', expected: total, read: returned, missing: 1 }]);
        assert.deepEqual(Object.keys(result.file), ['schemaVersion', 'roots']);
    }
});

test('false terminal flag with a gap does not discard resources on the following page', async () => {
    const result = await run([
        { info: { media_count: 2 }, has_more: false, medias: [video(1)] },
        { info: { media_count: 2 }, has_more: false, medias: [video(2)] },
    ]);
    assert.equal(result.stats.items, 2);
    assert.deepEqual(result.stats.countWarnings, []);
});

test('probe errors, repeated resources, cancellation and page cap still reject', async () => {
    await assert.rejects(run([{ has_more: false, info: { media_count: 2 }, medias: [video(1)] }]), /Unexpected request/);
    await assert.rejects(run([
        { has_more: false, info: { media_count: 2 }, medias: [video(1)] },
        { has_more: false, info: { media_count: 2 }, medias: [video(1)] },
    ]), /重复资源/);
    await assert.rejects(run([{ has_more: false, info: { media_count: 2 }, medias: [video(1)] }], undefined, { maxPages: 1 }), /安全上限/);
    const state = {}; let calls = 0;
    const api = { async getFolderMediaPage() {
        calls++;
        if (calls === 2) state.cancelRequested = true;
        return { has_more: false, info: { media_count: 2 }, medias: calls === 1 ? [video(1)] : [] };
    } };
    await assert.rejects(new Exporter(api, state).run([{ id: '1', title: '测试夹', mediaCount: 2 }]), CancelledError);
    assert.equal(calls, 2);
});

test('all resources omitted by API are a count warning, not skipped videos', async () => {
    const result = await run([{ info: { media_count: 5 }, has_more: false, medias: [] }]);
    assert.equal(result.stats.items, 0);
    assert.equal(result.stats.skipped, 0);
    assert.equal(result.stats.countWarnings[0].missing, 5);
});

test('out-of-range empty page info cannot overwrite the previous total or falsely report edits', async () => {
    for (const info of [{ media_count: 0 }, {}, undefined, { media_count: 99 }]) {
        const result = await run([
            { info: { media_count: 15 }, has_more: false, medias: Array.from({ length: 14 }, (_, i) => video(i + 1)) },
            { info, has_more: false, medias: null },
        ], { id: '1', title: '人工研究样例', mediaCount: 15 });
        assert.equal(result.stats.items, 14);
        assert.deepEqual(result.stats.countWarnings, [{ folder: '人工研究样例', expected: 15, read: 14, missing: 1 }]);
    }
});

test('missing has_more with terminal zero keeps the known count, and empty first page keeps list hint', async () => {
    const result = await run([
        { info: { media_count: 15 }, medias: [video(1)] },
        { info: { media_count: 0 }, medias: [] },
    ]);
    assert.equal(result.stats.countWarnings[0].expected, 15);
    const empty = await run([{ info: { media_count: 0 }, has_more: false, medias: [] }], { id: '1', title: '空返回样例', mediaCount: 5 });
    assert.equal(empty.stats.countWarnings[0].missing, 5);
});

test('nonempty inconsistent pages still fail without accusing the user of modifying anything', async () => {
    await assert.rejects(run([
        { info: { media_count: 15 }, has_more: true, medias: [video(1)] },
        { info: { media_count: 16 }, has_more: false, medias: [video(2)] },
    ]), /接口在非空页返回了不同总数（15 → 16）/);
});

test('page safety cap fails without partial success', async () => {
    await assert.rejects(run([{ has_more: true, medias: [video(1)] }], undefined, { maxPages: 1 }), /安全上限/);
});

test('folder failure never resolves an export result', async () => {
    let count = 0;
    const api = { async getFolderMediaPage() { if (++count === 2) throw new Error('second folder failed'); return { medias: [], has_more: false }; } };
    await assert.rejects(new Exporter(api, {}).run([{ id: '1', title: '甲' }, { id: '2', title: '乙' }]), /second folder failed/);
});

test('cancel current request prevents next page and folders', async () => {
    const state = {}, calls = [];
    const api = { async getFolderMediaPage(folder, page) { calls.push(page); state.cancelRequested = true; return { has_more: true, medias: [video(1)] }; } };
    await assert.rejects(new Exporter(api, state).run([{ id: '1', title: '甲' }, { id: '2', title: '乙' }]), CancelledError);
    assert.deepEqual(calls, [1]);
});

test('429/5xx/network errors use bounded exponential retries', async () => {
    const waits = []; let calls = 0;
    const api = new BiliApi({}, { wait: async ms => waits.push(ms), transport: async () => {
        calls++;
        if (calls === 1) return { status: 429 };
        if (calls === 2) return { status: 503 };
        if (calls === 3) throw Object.assign(new Error('network'), { retryable: true });
        return response({ isLogin: true, mid: 42, uname: 'fixture' });
    } });
    assert.equal((await api.getCurrentUser()).mid, '42');
    assert.deepEqual(waits, [200, 500, 1000, 2000]);
});

test('retry exhaustion and risk/access/API errors have context and stop', async () => {
    for (const res of [{ status: 412 }, { status: 403 }, { status: 401 }, response({}, -352), response({}, -101), { status: 200, responseText: '<html>challenge</html>' }, response(null), { status: 200, responseText: '{"data":{}}' }]) {
        let calls = 0;
        const api = new BiliApi({}, { wait: async () => {}, transport: async () => { calls++; return res; } });
        await assert.rejects(api.getCurrentUser(), /读取当前账号失败/);
        assert.equal(calls, 1);
    }
    let calls = 0;
    const api = new BiliApi({}, { wait: async () => {}, transport: async () => { calls++; return { status: 500 }; } });
    await assert.rejects(api.getCurrentUser(), /HTTP 500/); assert.equal(calls, 4);
});

test('cancel during backoff prevents all subsequent requests', async () => {
    const state = {}; let calls = 0;
    const api = new BiliApi(state, { wait: async ms => { if (ms === 500) state.cancelRequested = true; }, transport: async () => { calls++; return { status: 429 }; } });
    await assert.rejects(api.getCurrentUser(), CancelledError); assert.equal(calls, 1);
});

test('API extracts current user, validates folder list and builds fixed read paths', async () => {
    const urls = [];
    const api = new BiliApi({}, { wait: async () => {}, transport: async url => {
        urls.push(new URL(url));
        if (urls.length === 1) return response({ isLogin: true, mid: 42 });
        if (urls.length === 2) return response({ count: 1, list: [{ id: 101, title: '甲', media_count: 0 }] });
        return response({ has_more: false, medias: [] });
    } });
    const user = await api.getCurrentUser();
    const fs = await api.getCreatedFolders(user.mid);
    await api.getFolderMediaPage(fs[0], 3);
    assert.equal(urls[1].searchParams.get('up_mid'), '42');
    assert.equal(urls[2].pathname, '/x/v3/fav/resource/list');
    assert.equal(urls[2].searchParams.get('ps'), '20');
    assert.equal(urls[2].searchParams.get('pn'), '3');
    assert.equal(urls[2].searchParams.get('platform'), 'web');
    assert.ok(urls.every(url => url.origin === 'https://api.bilibili.com'));
});

test('login missing and malformed/incomplete folder listings stop', async () => {
    const apiFor = data => new BiliApi({}, { wait: async () => {}, transport: async () => response(data) });
    await assert.rejects(apiFor({ isLogin: false }).getCurrentUser(), /尚未登录/);
    await assert.rejects(apiFor({ isLogin: true, mid: 'bad' }).getCurrentUser(), /结构异常/);
    assert.deepEqual(await apiFor({ count: 0, list: null }).getCreatedFolders('1'), []);
    for (const data of [{ list: null }, { count: 2, list: [] }, { list: [{}] }, { list: [{ id: 1, title: '甲' }, { id: 1, title: '乙' }] }]) {
        await assert.rejects(apiFor(data).getCreatedFolders('1'));
    }
});

test('manager transport uses GET and ambient credentials without a cookie header', async () => {
    let request;
    global.GM_xmlhttpRequest = opts => { request = opts; opts.onload(response({})); };
    try {
        await gmRequest('https://api.bilibili.com/x/web-interface/nav');
        assert.equal(request.method, 'GET'); assert.equal(request.anonymous, false);
        assert.deepEqual(request.headers, { Accept: 'application/json' }); assert.equal(request.timeout, 30000);
    } finally { delete global.GM_xmlhttpRequest; }
});

test('parser byte/node limits prevent incompatible downloads; filename uses local date', () => {
    assert.throws(() => serializeExport({ roots: [{ name: '中'.repeat(Math.ceil(MAX_BYTES / 3)), children: [] }] }), /10 MB/);
    const root = { children: Array.from({ length: MAX_NODES }, () => ({})) };
    assert.throws(() => serializeExport({ roots: [root] }), /10000/);
    assert.equal(filename(new Date(2026, 8, 7, 0, 1)), 'resourcetree-bilibili-2026-09-07.json');
});

test('skip records bounded while all reasons counted', async () => {
    const result = await run([{ has_more: false, medias: Array.from({ length: 120 }, (_, i) => video(i + 1, { bvid: '' })) }]);
    assert.equal(result.stats.skipped, 120); assert.equal(result.stats.records.length, 100);
});

test('single video export keeps its parent folder, action, content and no extra items', async () => {
    const result = await new Exporter(mockApi(samplePages), {}, () => {}, deterministicOptions()).run(folders, 'tv.danmaku.bili');
    const item = result.file.roots[0].children[0].children[0];
    const single = selectVideos(result.file, new Set([item.id]));
    assert.equal(single.roots[0].name, '哔哩哔哩');
    assert.equal(single.roots[0].children.length, 1);
    assert.equal(single.roots[0].children[0].name, '生产力');
    assert.deepEqual(single.roots[0].children[0].children, [item]);
    assert.equal(result.file.roots[0].children.length, 2);
    assert.equal(readFileSync(join(__dirname, '../samples/resourcetree-bilibili.single.sample.json'), 'utf8'), serializeExport(single) + '\n');
    assert.throws(() => selectVideos(result.file, new Set()), /至少勾选/);
});

test('video selection uses membership UUIDs rather than global BV matching', async () => {
    const page = { has_more: false, medias: [video(1)] };
    const result = await new Exporter(mockApi({ '1': [page], '2': [page] }), {}).run([{ id: '1', title: '甲', mediaCount: 1 }, { id: '2', title: '乙', mediaCount: 1 }]);
    const item = result.file.roots[0].children[1].children[0];
    const single = selectVideos(result.file, new Set([item.id]));
    assert.equal(single.roots[0].children.length, 1);
    assert.equal(single.roots[0].children[0].name, '乙');
    assert.equal(single.roots[0].children[0].children.length, 1);
});

test('v2 exporter preserves the v1 golden tree exactly apart from the requested schema fields', async () => {
    const before = JSON.parse(readFileSync(join(__dirname, 'baselines/resourcetree-v1.json'), 'utf8'));
    const result = await new Exporter(mockApi(samplePages), {}, () => {}, deterministicOptions()).run(folders, 'tv.danmaku.bili');
    const report = validate(before, result.file, 'tv.danmaku.bili');
    assert.equal(report.items, 3);
    assert.equal(report.foldersIncludingRoot, 3);
});

test('default v2 export targets international Bilibili and never duplicates the BV in action', async () => {
    const result = await run([{ has_more: false, medias: [video(1, { bvid: 'BV1meMS6rE6Z' })] }]);
    assert.equal(result.file.schemaVersion, 2);
    const item = result.file.roots[0].children[0].children[0];
    assert.deepEqual(item.content, { type: 'TEXT', text: 'BV1meMS6rE6Z' });
    assert.deepEqual(item.action, { type: 'COPY_AND_LAUNCH', target: 'com.bilibili.app.in' });
    assert.equal(JSON.stringify(item).split('BV1meMS6rE6Z').length - 1, 1);
});
