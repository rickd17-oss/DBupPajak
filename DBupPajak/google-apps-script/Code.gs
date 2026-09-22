/**
 * API Google Drive untuk DBupPajak — NR17
 * Ubah ACCESS_CODE sebelum melakukan deployment.
 */
const ACCESS_CODE = 'GANTI-DENGAN-KODE-RAHASIA';

function doGet() {
  return json_({ ok: true, app: 'DBupPajak API', version: '1.0' });
}

function doPost(e) {
  try {
    const req = JSON.parse(e.postData.contents || '{}');
    if (req.accessCode !== ACCESS_CODE) return json_({ ok: false, message: 'Kode akses salah.' });
    if (req.action === 'ping') return ping_(req);
    if (req.action === 'createSubmission') return createSubmission_(req);
    if (req.action === 'uploadFile') return uploadFile_(req);
    return json_({ ok: false, message: 'Aksi tidak dikenali.' });
  } catch (err) {
    return json_({ ok: false, message: String(err.message || err) });
  }
}

function ping_(req) {
  const folder = DriveApp.getFolderById(cleanId_(req.folderId));
  return json_({ ok: true, folderName: folder.getName() });
}

function createSubmission_(req) {
  const root = DriveApp.getFolderById(cleanId_(req.folderId));
  const target = getOrCreateFolder_(root, safe_(req.folderName));
  const timestamp = Utilities.formatDate(new Date(), Session.getScriptTimeZone(), 'yyyy-MM-dd HH:mm:ss');
  const owners = (req.owners || []).map(function (x, i) {
    return (i + 1) + '. ' + x.name + ' — ' + (x.hasBuilding ? 'Ada bangunan' : 'Tidak ada bangunan');
  }).join('\n');
  const info = [
    'DBupPajak — NR17',
    'Waktu unggah: ' + timestamp,
    'Folder: ' + req.folderName,
    'Kebutuhan: ' + req.need,
    req.need === 'Mutasi Pecah' ? '' : 'Status bangunan: ' + (req.hasBuilding ? 'Ada bangunan' : 'Tidak ada bangunan'),
    owners ? '\nRincian pecahan:\n' + owners : '',
    '\nCatatan:\n' + (req.note || '-')
  ].join('\n');
  replaceTextFile_(target, 'data_pengajuan.txt', info);
  replaceTextFile_(target, 'catatan.txt', req.note || '-');
  CacheService.getScriptCache().put(target.getId(), target.getId(), 21600);
  return json_({ ok: true, submissionId: target.getId() });
}

function uploadFile_(req) {
  const folder = DriveApp.getFolderById(cleanId_(req.submissionId));
  const name = safe_(req.fileName);
  const old = folder.getFilesByName(name);
  while (old.hasNext()) old.next().setTrashed(true);
  const bytes = Utilities.base64Decode(req.data);
  const blob = Utilities.newBlob(bytes, req.mimeType || 'application/octet-stream', name);
  const file = folder.createFile(blob);
  return json_({ ok: true, fileId: file.getId(), fileName: file.getName() });
}

function getOrCreateFolder_(parent, name) {
  const found = parent.getFoldersByName(name);
  return found.hasNext() ? found.next() : parent.createFolder(name);
}

function replaceTextFile_(folder, name, content) {
  const old = folder.getFilesByName(name);
  while (old.hasNext()) old.next().setTrashed(true);
  folder.createFile(name, content, MimeType.PLAIN_TEXT);
}

function cleanId_(value) {
  const id = String(value || '').match(/[-\w]{20,}/);
  if (!id) throw new Error('ID folder tidak valid.');
  return id[0];
}

function safe_(value) {
  return String(value || '').replace(/[\\/:*?"<>|]/g, '-').trim().substring(0, 150);
}

function json_(data) {
  return ContentService.createTextOutput(JSON.stringify(data)).setMimeType(ContentService.MimeType.JSON);
}
