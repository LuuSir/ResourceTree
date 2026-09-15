'use strict';
// Replay the current Item converter over an existing v1 export. No network,
// reordering, new IDs, timestamps or changes to the original input file.
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const { convertMedia, validBvid } = require('../bilibili-fav-export.user.js');

function upgradeUsingCurrentConverter(before) {
    assert.equal(before.schemaVersion, 1);
    function visit(node) {
        if (node.type === 'folder') return { ...node, children: node.children.map(visit) };
        assert.equal(node.action.type, 'COPY_AND_LAUNCH');
        assert.equal(node.action.text, node.content);
        const converted = convertMedia({ type: 2, title: node.name, bvid: node.content },
            node.sortOrder, node.createdAt, node.action.packageName, () => node.id);
        assert.ok(converted.node, `Unable to convert item ${node.id}`);
        return { ...node, content: converted.node.content, action: converted.node.action };
    }
    return { ...before, schemaVersion: 2, roots: before.roots.map(visit) };
}

function validate(before, after, target = 'com.bilibili.app.in') {
    assert.equal(after.schemaVersion, 2);
    let folders = 0, items = 0;
    const ids = new Set(), bvSequence = [];
    function visit(node) {
        assert.ok(!ids.has(node.id), `Duplicate node ID ${node.id}`); ids.add(node.id);
        if (node.type === 'folder') { folders++; node.children.forEach(visit); return; }
        assert.equal(node.type, 'item'); items++;
        assert.deepEqual(Object.keys(node.content).sort(), ['text', 'type']);
        assert.equal(node.content.type, 'TEXT');
        assert.equal(validBvid(node.content.text), node.content.text);
        assert.ok(node.content.text.length > 2);
        assert.deepEqual(Object.keys(node.action).sort(), ['target', 'type']);
        assert.equal(node.action.type, 'COPY_AND_LAUNCH');
        assert.equal(node.action.target, target);
        assert.ok(!Object.hasOwn(node.action, 'text'));
        assert.ok(!Object.hasOwn(node.action, 'packageName'));
        bvSequence.push(node.content.text);
    }
    after.roots.forEach(visit);
    // Normalize only the three requested schema fields back to v1, then compare
    // every field and every array position, not merely aggregate counts.
    function restore(node) {
        if (node.type === 'folder') return { ...node, children: node.children.map(restore) };
        return { ...node, content: node.content.text,
            action: { type: node.action.type, text: node.content.text, packageName: node.action.target } };
    }
    assert.deepEqual({ ...after, schemaVersion: 1, roots: after.roots.map(restore) }, before);
    return { schemaVersion: 2, foldersIncludingRoot: folders, items, uniqueBvs: new Set(bvSequence).size,
        treeAndAllOtherFieldsUnchanged: true, bvMembershipAndOrderUnchanged: true, legacyActionFields: 0, target };
}

if (require.main === module) {
    const input = path.resolve(process.argv[2] || 'resourcetree-bilibili-2026-09-07.json');
    const output = path.resolve(process.argv[3] || 'tools/bilibili-export/samples/resourcetree-bilibili.v2.actual.json');
    assert.notEqual(input, output, 'Do not overwrite the original export');
    const before = JSON.parse(fs.readFileSync(input, 'utf8'));
    const after = upgradeUsingCurrentConverter(before);
    const report = validate(before, after);
    fs.writeFileSync(output, JSON.stringify(after, null, 2) + '\n', 'utf8');
    // Verify the on-disk artifact too.
    validate(before, JSON.parse(fs.readFileSync(output, 'utf8')));
    fs.writeFileSync(path.join(__dirname, 'v2-validation.json'), JSON.stringify({ input, output, ...report }, null, 2) + '\n');
    console.log(JSON.stringify(report, null, 2));
}
module.exports = { validate, upgradeUsingCurrentConverter };
