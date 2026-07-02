/**
 * Minimal PDF generator (no dependencies) — Android TV Remote features doc
 */
import fs from "fs";
import path from "path";
import { fileURLToPath } from "url";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const outPath = path.join(__dirname, "Android-TV-Remote-Features-and-Hurdles.pdf");

const lines = [];
function esc(s) {
  return s.replace(/\\/g, "\\\\").replace(/\(/g, "\\(").replace(/\)/g, "\\)");
}

let y = 780;
const left = 50;
const pageHeight = 842;
const bottom = 50;

function checkPage(doc) {
  if (y < bottom + 40) {
    doc.pages.push(doc.currentPage);
    doc.currentPage = [];
    y = 780;
    doc.currentPage.push("BT /F1 11 Tf");
  }
}

function addTitle(doc, text) {
  checkPage(doc);
  y -= 24;
  doc.currentPage.push(`1 0 0 1 ${left} ${y} Tm (${esc(text)}) Tj`);
  y -= 8;
}

function addHeading(doc, text) {
  checkPage(doc);
  y -= 18;
  doc.currentPage.push(`/F2 12 Tf 0.1 0.14 0.49 rg 1 0 0 1 ${left} ${y} Tm (${esc(text)}) Tj`);
  doc.currentPage.push("0 0 0 rg /F1 10 Tf");
  y -= 6;
}

function addSub(doc, text) {
  checkPage(doc);
  y -= 14;
  doc.currentPage.push(`/F2 10 Tf 1 0 0 1 ${left} ${y} Tm (${esc(text)}) Tj`);
  doc.currentPage.push("/F1 10 Tf");
  y -= 4;
}

function wrap(text, max = 92) {
  const words = text.split(" ");
  const out = [];
  let line = "";
  for (const w of words) {
    const test = line ? `${line} ${w}` : w;
    if (test.length > max) {
      if (line) out.push(line);
      line = w;
    } else {
      line = test;
    }
  }
  if (line) out.push(line);
  return out;
}

function addPara(doc, text) {
  for (const ln of wrap(text)) {
    checkPage(doc);
    y -= 13;
    doc.currentPage.push(`1 0 0 1 ${left} ${y} Tm (${esc(ln)}) Tj`);
  }
  y -= 4;
}

function addBullet(doc, text) {
  for (let i = 0; i < wrap(text, 88).length; i++) {
    const ln = wrap(text, 88)[i];
    checkPage(doc);
    y -= 13;
    const prefix = i === 0 ? "- " : "  ";
    doc.currentPage.push(`1 0 0 1 ${left + 8} ${y} Tm (${esc(prefix + ln)}) Tj`);
  }
}

function addHurdle(doc, title, problem, solution) {
  addSub(doc, title);
  addPara(doc, `Problem: ${problem}`);
  addPara(doc, `Solution: ${solution}`);
  y -= 4;
}

const doc = { pages: [[]], currentPage: [] };
doc.currentPage.push("BT /F2 22 Tf 0.1 0.14 0.49 rg");
doc.currentPage.push(`1 0 0 1 ${left} 780 Tm (${esc("Android TV Remote")}) Tj`);
doc.currentPage.push("/F1 13 Tf 0.33 0.33 0.33 rg");
doc.currentPage.push(`1 0 0 1 ${left} 755 Tm (${esc("Features and Development Hurdles")}) Tj`);
doc.currentPage.push("/F1 9 Tf");
doc.currentPage.push(`1 0 0 1 ${left} 735 Tm (${esc("Project: AndroidTVRemote  |  Date: June 30, 2026")}) Tj`);
doc.currentPage.push("0 0 0 rg /F1 10 Tf");
y = 710;

addTitle(doc, "1. App Overview");
addPara(
  doc,
  "WiFi smart remote and casting app for Android TV / Google TV using protocol v2 (TLS ports 6466/6467) plus Google Cast. Built in Kotlin with MVVM: remotecontrol library module + app UI module."
);

addTitle(doc, "2. Features");

addHeading(doc, "2.1 Settings - TV Connection");
addBullet(doc, "mDNS scan for Android TVs on WiFi");
addBullet(doc, "Tap TV to pair; 6-char code entry; Complete Pairing button");
addBullet(doc, "Manual IP fallback; Get new code; Reconnect without re-pairing");
addBullet(doc, "Saved pairing state; phone IP display; live status labels");

addHeading(doc, "2.2 Remote Tab");
addBullet(doc, "D-pad, Home, Back, Power, Apps, TV Input, Voice Search");
addBullet(doc, "Volume, Channel, Mute, Play/Pause, Rewind, Forward");
addBullet(doc, "Shortcuts: Netflix, YouTube, Prime Video, Hotstar");
addBullet(doc, "Connection status; jump to Cast tab");

addHeading(doc, "2.3 Cast Tab");
addBullet(doc, "Google Cast device picker (MediaRouter)");
addBullet(doc, "Cast photos (Downloads/Gallery), videos, audio");
addBullet(doc, "Screen mirror via MediaProjection + MJPEG HTTP stream");
addBullet(doc, "Foreground service + notification while mirroring");

addHeading(doc, "2.4 Session & Architecture");
addBullet(doc, "Remote pauses during cast/mirror; auto-resumes after");
addBullet(doc, "MVVM: ViewModels, Repositories, AppContainer DI");
addBullet(doc, "StateFlow UI; ViewBinding; glass-style bottom nav UI");

addHeading(doc, "2.5 Reliability");
addBullet(doc, "SafeRun error wrapping; OperationResult for cast");
addBullet(doc, "CrashGuard logging; safe fragments and NSD/Cast handling");

addTitle(doc, "3. Modules");
addBullet(doc, "app: ui/, data/, di/, util/");
addBullet(doc, "remotecontrol: PairingManager, RemoteManager, TLS, certs");

addTitle(doc, "4. Development Hurdles");

addHeading(doc, "Pairing & UX");
addHurdle(doc, "Submit button / pair loop", "Code on TV but no submit; Pair again re-sent code.", "Complete Pairing + waitingForCode guard.");
addHurdle(doc, "Manual IP first", "Users wanted tap-to-pair from scan list.", "TV cards primary; manual IP optional.");
addHurdle(doc, "Re-pair after cast", "Cast broke remote; endless new pairing codes.", "PairingStore, connect vs pair, ConnectionCoordinator pause/resume.");

addHeading(doc, "Threading & Lifecycle");
addHurdle(doc, "Wrong thread UI", "Callbacks updated UI off main thread.", "mainHandler post on all manager callbacks.");
addHurdle(doc, "Binding NPE", "Tab switch crashed on null binding.", "_binding guards; repeatOnLifecycle.");
addHurdle(doc, "Fragment transaction crash", "commit during state save.", "isStateSaved / isDestroyed checks.");

addHeading(doc, "Build & Code");
addHurdle(doc, "Windows path too long", "Gradle transform MAX_PATH error.", "Short build dir in build.gradle.kts.");
addHurdle(doc, "combine() 5-flow limit", "SettingsViewModel compile error.", "Nested combine for 6+ flows.");
addHurdle(doc, "MVVM refactor duplicates", "Old root files caused duplicate class errors.", "Deleted legacy files; manifest path updates.");

addHeading(doc, "Cast vs Remote");
addHurdle(doc, "Protocols conflict", "Cast dropped remote socket.", "Separate session modes; pause remote on cast.");
addHurdle(doc, "Auto-pair fallback", "8s timeout re-paired already-paired TVs.", "Fallback only on explicit pair; reconnect backoff.");

addHeading(doc, "Stability");
addHurdle(doc, "Unhandled exceptions", "NSD, Cast, projection could crash.", "SafeRun, OperationResult, protocol try/catch.");

addTitle(doc, "5. Test Checklist");
addBullet(doc, "Pair, remote keys, tab switch during scan");
addBullet(doc, "Cast photo/video; mirror start/stop; remote pause/resume");
addBullet(doc, "Reconnect paired TV without new code");
addBullet(doc, "Deny permissions — no crash");

addTitle(doc, "6. Limitations");
addBullet(doc, "Same WiFi required; emulator cannot reach real TV");
addBullet(doc, "Mirror quality depends on network (MJPEG over HTTP)");
addBullet(doc, "Cast and Remote are separate protocols, coordinated in app");

doc.pages.push(doc.currentPage);

// Build PDF
const objects = [];
let objNum = 0;
const offsets = [0];

function addObj(content) {
  objNum++;
  objects.push(`${objNum} 0 obj\n${content}\nendobj\n`);
  return objNum;
}

const fontRegular = addObj("<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>");
const fontBold = addObj("<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica-Bold >>");

const pageObjNums = [];
const contentObjNums = [];

for (const pageContent of doc.pages) {
  const stream = `${pageContent.join("\n")}\nET`;
  const contentNum = addObj(`<< /Length ${stream.length} >>\nstream\n${stream}\nendstream`);
  contentObjNums.push(contentNum);
  const pageNum = addObj(
    `<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] /Contents ${contentNum} 0 R /Resources << /Font << /F1 ${fontRegular} 0 R /F2 ${fontBold} 0 R >> >> >>`
  );
  pageObjNums.push(pageNum);
}

const kids = pageObjNums.map((n) => `${n} 0 R`).join(" ");
const pagesNum = addObj(`<< /Type /Pages /Kids [${kids}] /Count ${pageObjNums.length} >>`);
// Fix parent refs - pages object is obj 2 in final but we need dynamic - rebuild

// Simpler: single pass with fixed catalog
objects.length = 0;
objNum = 0;
offsets.length = 1;

const f1 = addObj("<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>");
const f2 = addObj("<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica-Bold >>");
const pageNums = [];
const contentNums = [];

for (const pageContent of doc.pages) {
  const stream = `${pageContent.join("\n")}\nET`;
  contentNums.push(addObj(`<< /Length ${stream.length} >>\nstream\n${stream}\nendstream`));
}

const pagesPlaceholder = objNum + doc.pages.length + 1;
for (let i = 0; i < doc.pages.length; i++) {
  pageNums.push(
    addObj(
      `<< /Type /Page /Parent ${pagesPlaceholder} 0 R /MediaBox [0 0 595 842] /Contents ${contentNums[i]} 0 R /Resources << /Font << /F1 ${f1} 0 R /F2 ${f2} 0 R >> >> >>`
    )
  );
}
const pagesObj = addObj(
  `<< /Type /Pages /Kids [${pageNums.map((n) => `${n} 0 R`).join(" ")}] /Count ${pageNums.length} >>`
);
const catalog = addObj(`<< /Type /Catalog /Pages ${pagesObj} 0 R >>`);

let pdf = "%PDF-1.4\n";
offsets[0] = 0;
for (let i = 0; i < objects.length; i++) {
  offsets.push(pdf.length);
  pdf += objects[i];
}
const xrefPos = pdf.length;
pdf += `xref\n0 ${objects.length + 1}\n`;
pdf += "0000000000 65535 f \n";
for (let i = 1; i <= objects.length; i++) {
  pdf += `${String(offsets[i]).padStart(10, "0")} 00000 n \n`;
}
pdf += `trailer\n<< /Size ${objects.length + 1} /Root ${catalog} 0 R >>\nstartxref\n${xrefPos}\n%%EOF\n`;

fs.writeFileSync(outPath, pdf);
console.log("PDF written:", outPath);
