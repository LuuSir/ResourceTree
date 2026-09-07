'use strict';
// Entirely invented data. No requests, account identifiers or real favorites.
const folders = [
    { id: '101', title: '生产力', mediaCount: 2 },
    { id: '102', title: '生命力', mediaCount: 1 },
];
const video = (id, extra = {}) => ({ id, type: 2, title: `示例视频 ${id}`, bvid: `BVExample${id}`, ...extra });
const samplePages = {
    '101': [{ info: { media_count: 2 }, has_more: false, medias: [video(1, { title: '示例视频 A' }), video(2, { title: '示例视频 B' })] }],
    '102': [{ info: { media_count: 1 }, has_more: false, medias: [video(3, { title: '示例视频 C' })] }],
};
function mockApi(pages, calls = []) {
    return { async getFolderMediaPage(folder, page) {
        calls.push([folder.id, page]);
        const result = pages[folder.id]?.[page - 1];
        if (!result) throw new Error(`Unexpected request: ${folder.id} page ${page}`);
        return result;
    } };
}
function deterministicOptions() {
    let nextId = 0;
    return { now: 1788739200000, newId: () => `00000000-0000-4000-8000-${String(++nextId).padStart(12, '0')}` };
}
module.exports = { folders, samplePages, video, mockApi, deterministicOptions };
