import fs from 'fs';
import path from 'path';
import crypto from 'crypto';
import zlib from 'zlib';

const PROJECT_ID = 'gen-lang-client-0216629575';
const SITE_ID = 'gen-lang-client-0216629575';
const WEB_DIR = path.resolve('web');

async function getAccessToken() {
  const res = await fetch('http://metadata.google.internal/computeMetadata/v1/instance/service-accounts/default/token', {
    headers: { 'Metadata-Flavor': 'Google' }
  });
  if (!res.ok) {
    throw new Error(`Failed to fetch metadata token: ${res.status} ${await res.text()}`);
  }
  const data = await res.json();
  return data.access_token;
}

function walkDir(dir, baseDir = dir) {
  let results = [];
  const entries = fs.readdirSync(dir, { withFileTypes: true });
  for (const entry of entries) {
    const fullPath = path.join(dir, entry.name);
    if (entry.isDirectory()) {
      results = results.concat(walkDir(fullPath, baseDir));
    } else if (entry.isFile()) {
      const relPath = '/' + path.relative(baseDir, fullPath).split(path.sep).join('/');
      results.push({ fullPath, relPath });
    }
  }
  return results;
}

async function apiRequest(url, method = 'GET', body = null, token) {
  const headers = {
    'Authorization': `Bearer ${token}`,
    'x-goog-user-project': PROJECT_ID
  };
  if (body) {
    headers['Content-Type'] = 'application/json';
  }

  const res = await fetch(url, {
    method,
    headers,
    body: body ? JSON.stringify(body) : undefined
  });

  const text = await res.text();
  let json;
  try {
    json = JSON.parse(text);
  } catch {
    json = text;
  }

  if (!res.ok) {
    throw new Error(`API error ${res.status} on ${method} ${url}: ${JSON.stringify(json)}`);
  }
  return json;
}

async function deploy() {
  console.log(`[1/6] Acquiring Google Cloud service account token...`);
  const token = await getAccessToken();
  console.log(`Token acquired successfully.`);

  console.log(`[2/6] Reading and gzip-compressing files from ${WEB_DIR}...`);
  const files = walkDir(WEB_DIR);
  console.log(`Found ${files.length} files in web directory.`);

  const fileHashMap = {};
  const hashToGzipBuf = {};
  const hashToName = {};

  for (const file of files) {
    const rawBuf = fs.readFileSync(file.fullPath);
    // Firebase Hosting requires raw deflate or gzip; zlib.gzipSync produces standard gzip
    const gzippedBuf = zlib.gzipSync(rawBuf, { level: 9 });
    const hash = crypto.createHash('sha256').update(gzippedBuf).digest('hex');
    fileHashMap[file.relPath] = hash;
    hashToGzipBuf[hash] = gzippedBuf;
    hashToName[hash] = file.relPath;
  }

  console.log(`[3/6] Creating new Firebase Hosting version for site '${SITE_ID}'...`);
  const versionConfig = {
    headers: [
      {
        glob: "/.well-known/assetlinks.json",
        headers: {
          "Content-Type": "application/json",
          "Access-Control-Allow-Origin": "*",
          "Cache-Control": "no-cache, no-store, must-revalidate"
        }
      }
    ],
    rewrites: [
      {
        glob: "/u/**",
        path: "/u/index.html"
      },
      {
        glob: "**",
        path: "/index.html"
      }
    ]
  };

  const createVersionRes = await apiRequest(
    `https://firebasehosting.googleapis.com/v1beta1/sites/${SITE_ID}/versions`,
    'POST',
    { config: versionConfig },
    token
  );

  const versionName = createVersionRes.name;
  console.log(`Version created: ${versionName}`);

  console.log(`[4/6] Populating files map (${Object.keys(fileHashMap).length} files)...`);
  const populateRes = await apiRequest(
    `https://firebasehosting.googleapis.com/v1beta1/${versionName}:populateFiles`,
    'POST',
    { files: fileHashMap },
    token
  );

  const uploadUrl = populateRes.uploadUrl;
  const requiredHashes = populateRes.uploadRequiredHashes || [];
  console.log(`Files needing upload: ${requiredHashes.length}`);

  for (let i = 0; i < requiredHashes.length; i++) {
    const hash = requiredHashes[i];
    const name = hashToName[hash] || hash;
    const gzipBuf = hashToGzipBuf[hash];
    console.log(`Uploading [${i + 1}/${requiredHashes.length}] ${name} (${(gzipBuf.length / 1024).toFixed(1)} KB gzipped)...`);

    const upRes = await fetch(`${uploadUrl}/${hash}`, {
      method: 'POST',
      headers: {
        'Authorization': `Bearer ${token}`,
        'x-goog-user-project': PROJECT_ID,
        'Content-Type': 'application/octet-stream'
      },
      body: gzipBuf
    });

    if (!upRes.ok) {
      throw new Error(`Upload failed for ${name}: ${upRes.status} ${await upRes.text()}`);
    }
  }

  console.log(`[5/6] Finalizing version ${versionName}...`);
  await apiRequest(
    `https://firebasehosting.googleapis.com/v1beta1/${versionName}?updateMask=status`,
    'PATCH',
    { status: 'FINALIZED' },
    token
  );
  console.log(`Version status updated to FINALIZED.`);

  console.log(`[6/6] Creating release on site '${SITE_ID}'...`);
  const releaseRes = await apiRequest(
    `https://firebasehosting.googleapis.com/v1beta1/sites/${SITE_ID}/releases?versionName=${encodeURIComponent(versionName)}`,
    'POST',
    { message: 'Deploy modernized Udhaar showcase & AssetLinks' },
    token
  );

  console.log(`Deployment SUCCESSFUL!`);
  console.log(`Release details:`, releaseRes);
  console.log(`Default URL: https://${SITE_ID}.web.app`);
  console.log(`Custom Domain: https://udhaar.jyotirmoyb.com/`);
}

deploy().catch(err => {
  console.error('Deployment error:', err);
  process.exit(1);
});
