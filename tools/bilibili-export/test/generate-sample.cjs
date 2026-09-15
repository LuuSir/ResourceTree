'use strict';
const { writeFileSync } = require('node:fs');
const { join } = require('node:path');
const { Exporter, selectVideos, serializeExport } = require('../bilibili-fav-export.user.js');
const { folders, samplePages, mockApi, deterministicOptions } = require('./fixtures.cjs');
(async () => {
    // Preserve the existing sample's target; current Android tests consume it.
    const result = await new Exporter(mockApi(samplePages), {}, () => {}, deterministicOptions()).run(folders, 'tv.danmaku.bili');
    writeFileSync(join(__dirname, '../samples/resourcetree-bilibili.sample.json'), result.text + '\n', 'utf8');
    const single = selectVideos(result.file, new Set([result.file.roots[0].children[0].children[0].id]));
    writeFileSync(join(__dirname, '../samples/resourcetree-bilibili.single.sample.json'), serializeExport(single) + '\n', 'utf8');
})().catch(error => { console.error(error); process.exitCode = 1; });
