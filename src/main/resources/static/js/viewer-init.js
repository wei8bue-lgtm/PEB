/**
 * viewer-init.js – Three.js 3D Viewer
 *
 * Unterstützte Formate:
 *   GLB/GLTF  → GLTFLoader (nativ Three.js)
 *   JT        → Server-seitige Konvertierung via /api/v1/viewer/{id}/glb
 *   PDF       → PDF.js Embed
 */

import * as THREE       from 'three';
import { GLTFLoader }   from 'three/addons/loaders/GLTFLoader.js';
import { OrbitControls} from 'three/addons/controls/OrbitControls.js';

// ── Three.js Kern-Objekte ─────────────────────────────────────
let renderer, scene, camera, controls;
let currentModel        = null;
let wireframeMode       = false;
let currentView         = 'perspective';
let selectedDatasetId   = null;
let selectedDownloadUrl = null;

// ── DOM-Referenzen ────────────────────────────────────────────
const canvas      = document.getElementById('viewerCanvas');
const placeholder = document.getElementById('viewerPlaceholder');
const loading     = document.getElementById('viewerLoading');
const progressBar = document.getElementById('progressBar');
const loadingText = document.getElementById('loadingText');
const loadingPct  = document.getElementById('loadingPercent');

// ── Format-Konstanten ─────────────────────────────────────────
const JT_EXTENSIONS  = new Set(['.jt']);
const GLB_EXTENSIONS = new Set(['.glb', '.gltf']);

// ══════════════════════════════════════════════════════════════
// INITIALISIERUNG
// ══════════════════════════════════════════════════════════════

function initThree() {
    renderer = new THREE.WebGLRenderer({
        canvas:    canvas,
        antialias: true,
        alpha:     false
    });
    renderer.setPixelRatio(window.devicePixelRatio);
    renderer.setSize(canvas.clientWidth || 800, canvas.clientHeight || 460);
    renderer.shadowMap.enabled       = true;
    renderer.shadowMap.type          = THREE.PCFSoftShadowMap;
    renderer.toneMapping             = THREE.ACESFilmicToneMapping;
    renderer.toneMappingExposure     = 1.2;
    renderer.outputColorSpace        = THREE.SRGBColorSpace;

    scene            = new THREE.Scene();
    scene.background = new THREE.Color(0x1a1a2e);
    scene.fog        = new THREE.FogExp2(0x1a1a2e, 0.02);

    camera = new THREE.PerspectiveCamera(
        45,
        (canvas.clientWidth || 800) / (canvas.clientHeight || 460),
        0.01,
        10000
    );
    camera.position.set(3, 2, 5);

    setupLights();

    const grid = new THREE.GridHelper(20, 20, 0x444466, 0x333355);
    grid.name  = 'grid';
    scene.add(grid);

    controls = new OrbitControls(camera, renderer.domElement);
    controls.enableDamping      = true;
    controls.dampingFactor      = 0.08;
    controls.screenSpacePanning = true;
    controls.minDistance        = 0.1;
    controls.maxDistance        = 5000;

    window.addEventListener('resize', onWindowResize);
    animate();

    window.dispatchEvent(new CustomEvent('viewerReady', {
        detail: { message: '✓ Three.js bereit (GLB + JT via Server)' }
    }));

    const loaderStatus = document.getElementById('loaderStatus');
    if (loaderStatus) {
        loaderStatus.textContent =
            '✓ Viewer bereit – GLB nativ, JT via Server-Konvertierung';
    }
}

function setupLights() {
    scene.add(new THREE.AmbientLight(0xffffff, 0.5));

    const dirLight = new THREE.DirectionalLight(0xffffff, 1.2);
    dirLight.position.set(5, 10, 7);
    dirLight.castShadow            = true;
    dirLight.shadow.mapSize.set(2048, 2048);
    dirLight.shadow.camera.near    = 0.1;
    dirLight.shadow.camera.far     = 100;
    scene.add(dirLight);

    scene.add(new THREE.HemisphereLight(0x8888ff, 0x444422, 0.4));

    const point = new THREE.PointLight(0x6688cc, 0.8, 50);
    point.position.set(-5, 5, -5);
    scene.add(point);
}

// ══════════════════════════════════════════════════════════════
// FORMAT-ROUTING
// ══════════════════════════════════════════════════════════════

function resolveLoadStrategy(fileName, category) {
    if (!fileName) return 'unknown';

    const lower = fileName.toLowerCase();
    const ext   = '.' + lower.split('.').pop();

    if (GLB_EXTENSIONS.has(ext))     return 'glb';
    if (JT_EXTENSIONS.has(ext))      return 'jt';
    if (ext === '.pdf')              return 'pdf';

    if (category === 'PDF_DOCUMENT') return 'pdf';
    if (['DESIGN_NX', 'DESIGN_INVENTOR',
         'DESIGN_SOLIDEDGE'].includes(category)) return 'jt';
    if (category === 'DESIGN_RENDERING')          return 'glb';

    return 'unknown';
}

// ══════════════════════════════════════════════════════════════
// SELECT ASSEMBLY
// ══════════════════════════════════════════════════════════════

window.selectAssembly = function(element) {

    document.querySelectorAll('.assembly-item')
            .forEach(el => el.classList.remove('active'));
    element.classList.add('active');

    // Vorherige Fallback-Warnungen entfernen
    clearFallbackWarnings();

    const isCompatible = (element.dataset.viewerCompatible || 'false')
                         .toLowerCase().trim() === 'true';

    selectedDatasetId   = element.dataset.datasetId;
    selectedDownloadUrl = element.dataset.downloadUrl;

    const downloadBtn = document.getElementById('btn-download');
    if (downloadBtn) downloadBtn.disabled = false;

    if (!isCompatible) {
        showNotViewable(element.dataset.fileName, element.dataset.category);
        return;
    }

    const fileName  = element.dataset.fileName  || '';
    const category  = element.dataset.category  || '';
    const glbUrl    = element.dataset.glbUrl;
    const datasetId = element.dataset.datasetId;

    updateInfoBar(element);

    const strategy = resolveLoadStrategy(fileName, category);

    switch (strategy) {
        case 'glb':
            loadGlbDirect(glbUrl || element.dataset.downloadUrl,
                          fileName, category);
            break;
        case 'jt':
            loadJtViaServer(datasetId, fileName, category,
                            element.dataset.downloadUrl);
            break;
        case 'pdf':
            showPdfViewer(element.dataset.downloadUrl, fileName);
            break;
        default:
            showNotViewable(fileName, category);
    }
};

// ══════════════════════════════════════════════════════════════
// GLB DIREKT LADEN
// ══════════════════════════════════════════════════════════════

function loadGlbDirect(url, fileName, category) {
    showLoading('Lade GLB-Modell...');

    const loader = new GLTFLoader();
    loader.load(
        url,
        (gltf) => loadModelIntoScene(gltf, fileName, category),
        (xhr) => {
            if (xhr.lengthComputable) {
                const pct = Math.round((xhr.loaded / xhr.total) * 100);
                updateProgress(pct, `GLB laden... ${pct}%`);
            }
        },
        (error) => {
            console.error('GLB-Ladefehler:', error);
            showError(`GLB konnte nicht geladen werden:\n${fileName}`);
        }
    );
}

// ══════════════════════════════════════════════════════════════
// JT VIA SERVER LADEN
// ══════════════════════════════════════════════════════════════

/**
 * Lädt eine JT-Datei über den Server-Konvertierungs-Endpunkt.
 *
 * Ablauf:
 *   1. Konverter-Status prüfen (GET /api/v1/viewer/converter-status)
 *   2. Falls kein Konverter → sofort informativen Hinweis zeigen
 *   3. Falls Konverter → GLB laden und auf isFallback prüfen
 *
 * @param {string} datasetId    - Dataset-ID
 * @param {string} fileName     - Originaldateiname (z.B. "part.jt")
 * @param {string} category     - Dataset-Kategorie
 * @param {string} downloadUrl  - URL zum Download der Originaldatei
 */
async function loadJtViaServer(datasetId, fileName,
                                category, downloadUrl) {
    const glbUrl = `/api/v1/viewer/${datasetId}/glb`;

    showLoading('Prüfe JT-Konverter...', true);

    try {
        // ── Schritt 1: Konverter-Status prüfen ──────────────────
        const statusResp = await fetch('/api/v1/viewer/converter-status');

        if (statusResp.ok) {
            const status = await statusResp.json();

            if (!status.converterAvailable) {
                // Konverter nicht konfiguriert → klare Meldung
                showJtNoConverterInfo(fileName, downloadUrl,
                                      status.converterPath);
                return;
            }
        }

        // ── Schritt 2: GLB laden ─────────────────────────────────
        updateProgress(20, 'Konvertiere JT → GLB (Server)...');

        await loadJtGlbWithProgress(glbUrl, fileName,
                                    category, downloadUrl);

    } catch (error) {
        console.error('JT-Ladefehler:', error);
        showError(
            `JT-Datei konnte nicht geladen werden:\n${fileName}`,
            true,
            downloadUrl
        );
    }
}

function loadJtGlbWithProgress(glbUrl, fileName,
                                category, downloadUrl) {
    return new Promise((resolve, reject) => {

        const loader = new GLTFLoader();

        loader.load(
            glbUrl,

            // ── onLoad ──────────────────────────────────────────
            (gltf) => {
                const extras = gltf.asset?.extras;

                if (extras?.isFallback) {
                    // Server hat Fallback geliefert → trotzdem anzeigen
                    // aber mit deutlichem Hinweis
                    loadModelIntoScene(gltf, fileName, category);
                    showFallbackWarning(fileName, downloadUrl);
                } else {
                    // Echte Konvertierung erfolgreich
                    loadModelIntoScene(gltf, fileName, category);
                }
                resolve();
            },

            // ── onProgress ──────────────────────────────────────
            (xhr) => {
                if (xhr.lengthComputable) {
                    const pct = 20 + Math.round(
                        (xhr.loaded / xhr.total) * 80);
                    updateProgress(pct, `Lade konvertiertes GLB... ${pct}%`);
                } else {
                    updateProgress(-1, 'Lade konvertiertes GLB...');
                }
            },

            // ── onError ─────────────────────────────────────────
            (error) => {
                console.error('JT-GLB-Ladefehler:', error);
                reject(error);
            }
        );
    });
}

// ══════════════════════════════════════════════════════════════
// MODELL IN SZENE LADEN
// ══════════════════════════════════════════════════════════════

function loadModelIntoScene(gltf, fileName, category) {

    if (currentModel) {
        scene.remove(currentModel);
        disposeModel(currentModel);
        currentModel = null;
    }

    const model  = gltf.scene;
    const box    = new THREE.Box3().setFromObject(model);
    const size   = box.getSize(new THREE.Vector3());
    const center = box.getCenter(new THREE.Vector3());

    model.position.sub(center);

    const maxDim = Math.max(size.x, size.y, size.z);
    if (maxDim > 0) {
        model.scale.setScalar(4 / maxDim);
    }

    model.traverse(child => {
        if (child.isMesh) {
            child.castShadow    = true;
            child.receiveShadow = true;
            if (child.material) child.material.needsUpdate = true;
        }
    });

    scene.add(model);
    currentModel = model;

    fitCameraToModel(box, size);
    controls.target.set(0, 0, 0);
    controls.update();

    hideLoading();
    showCanvas();

    const polyCount = countPolygons(model);
    const polyEl    = document.getElementById('infoPolycount');
    if (polyEl) {
        polyEl.textContent = `Polygone: ${polyCount.toLocaleString('de-DE')}`;
    }

    if (wireframeMode) applyWireframe(true);

    console.info(`Modell geladen: ${fileName} | Polygone: ${polyCount}`);
}

function fitCameraToModel(box, size) {
    const maxDim  = Math.max(size.x, size.y, size.z);
    const fov     = camera.fov * (Math.PI / 180);
    let   camDist = Math.abs(maxDim / Math.sin(fov / 2)) * 0.8;
    camDist       = Math.max(camDist, 2);

    camera.position.set(camDist * 0.7, camDist * 0.5, camDist);
    camera.near = camDist * 0.001;
    camera.far  = camDist * 100;
    camera.updateProjectionMatrix();
    controls.update();
}

function countPolygons(model) {
    let count = 0;
    model.traverse(child => {
        if (child.isMesh && child.geometry) {
            const geo = child.geometry;
            count += geo.index
                ? geo.index.count / 3
                : (geo.attributes.position?.count ?? 0) / 3;
        }
    });
    return Math.round(count);
}

function disposeModel(model) {
    model.traverse(child => {
        if (child.isMesh) {
            child.geometry?.dispose();
            if (Array.isArray(child.material)) {
                child.material.forEach(m => m.dispose());
            } else {
                child.material?.dispose();
            }
        }
    });
}

// ══════════════════════════════════════════════════════════════
// PDF VIEWER
// ══════════════════════════════════════════════════════════════

function showPdfViewer(downloadUrl, fileName) {
    hideLoading();
    canvas.style.display      = 'none';
    placeholder.style.display = 'flex';
    placeholder.innerHTML     = `
        <div style="display:flex;flex-direction:column;align-items:center;
                    gap:1rem;color:rgba(255,255,255,0.7);">
            <div style="font-size:3rem;">📋</div>
            <p style="margin:0;font-size:1rem;font-weight:500;">
                ${escapeHtml(fileName)}
            </p>
            <p style="margin:0;font-size:0.85rem;
                      color:rgba(255,255,255,0.5);">
                PDF kann direkt heruntergeladen werden
            </p>
            <a href="${escapeHtml(downloadUrl)}"
               style="margin-top:0.5rem;padding:0.5rem 1.5rem;
                      background:#0066cc;color:white;
                      border-radius:4px;text-decoration:none;">
                📥 PDF herunterladen
            </a>
        </div>`;
}

// ══════════════════════════════════════════════════════════════
// TOOLBAR
// ══════════════════════════════════════════════════════════════

window.setView = function(view) {
    currentView = view;

    document.querySelectorAll('.viewer-btn[id^="btn-"]')
            .forEach(b => b.classList.remove('active'));

    document.getElementById('btn-' + view)
            ?.classList.add('active');

    if (!camera || !controls) return;

    const dist = camera.position.length();

    const positions = {
        front:       [0, 0, dist],
        top:         [0, dist, 0],
        side:        [dist, 0, 0],
        perspective: [dist * 0.6, dist * 0.4, dist * 0.8]
    };

    const ups = {
        top: [0, 0, -1]
    };

    const pos = positions[view] ?? positions.perspective;
    camera.position.set(...pos);
    camera.up.set(...(ups[view] ?? [0, 1, 0]));
    controls.target.set(0, 0, 0);
    controls.update();

    const infoView = document.getElementById('infoView');
    if (infoView) {
        infoView.textContent = 'Ansicht: '
            + view.charAt(0).toUpperCase() + view.slice(1);
    }
};

window.resetView     = () => { window.setView('perspective');
                                controls?.reset(); };

window.toggleWireframe = function() {
    wireframeMode = !wireframeMode;
    applyWireframe(wireframeMode);
    document.getElementById('btn-wireframe')
            ?.classList.toggle('active', wireframeMode);
};

function applyWireframe(enabled) {
    if (!currentModel) return;
    currentModel.traverse(child => {
        if (child.isMesh && child.material) {
            const mats = Array.isArray(child.material)
                         ? child.material : [child.material];
            mats.forEach(m => m.wireframe = enabled);
        }
    });
}

window.downloadSelected = function() {
    if (selectedDownloadUrl) window.location.href = selectedDownloadUrl;
};

// ══════════════════════════════════════════════════════════════
// UI-ZUSTÄNDE
// ══════════════════════════════════════════════════════════════

function showLoading(text = 'Lade...', indeterminate = false) {
    canvas.style.display      = 'none';
    placeholder.style.display = 'none';
    loading.style.display     = 'flex';
    if (loadingText) loadingText.textContent   = text;
    if (progressBar) progressBar.style.width   =
        indeterminate ? '30%' : '0%';
    if (loadingPct)  loadingPct.textContent    = '';
}

function hideLoading() {
    loading.style.display = 'none';
}

function showCanvas() {
    canvas.style.display      = 'block';
    placeholder.style.display = 'none';
}

function updateProgress(pct, text) {
    if (loadingText) loadingText.textContent = text || '';
    if (pct >= 0) {
        if (progressBar) progressBar.style.width = pct + '%';
        if (loadingPct)  loadingPct.textContent  = pct + '%';
    }
}

/**
 * Zeigt eine Fehlermeldung im Placeholder-Bereich.
 *
 * @param {string}  message      - Fehlermeldungstext
 * @param {boolean} withDownload - Download-Button anzeigen?
 * @param {string}  downloadUrl  - URL für Download-Button
 */
function showError(message, withDownload = false, downloadUrl = null) {
    hideLoading();
    canvas.style.display      = 'none';
    placeholder.style.display = 'flex';

    const dlUrl = downloadUrl || selectedDownloadUrl;
    const dlButton = (withDownload && dlUrl)
        ? `<a href="${escapeHtml(dlUrl)}"
              style="margin-top:1rem;padding:0.5rem 1.5rem;
                     background:#0066cc;color:white;
                     border-radius:4px;text-decoration:none;">
               📥 Original herunterladen
           </a>`
        : '';

    placeholder.innerHTML = `
        <div class="viewer-placeholder-icon">⚠️</div>
        <p style="margin:0;color:rgba(255,100,100,0.9);
                  font-size:0.95rem;max-width:420px;
                  text-align:center;white-space:pre-line;">
            ${escapeHtml(message)}
        </p>
        ${dlButton}`;
}

/**
 * Zeigt eine informative Meldung wenn kein JT-Konverter konfiguriert ist.
 * Klarer als eine generische Fehlermeldung.
 *
 * @param {string} fileName      - JT-Dateiname
 * @param {string} downloadUrl   - Download-URL der Originaldatei
 * @param {string} converterPath - Konfigurierter Konverter-Pfad (oder leer)
 */
function showJtNoConverterInfo(fileName, downloadUrl, converterPath) {
    hideLoading();
    canvas.style.display      = 'none';
    placeholder.style.display = 'flex';

    const pathHint = converterPath
        ? `Konfigurierter Pfad: <code>${escapeHtml(converterPath)}</code>
           (Tool nicht gefunden)`
        : `Setze <code>peb.viewer.converter.path</code>
           in <code>application.properties</code>`;

    const dlButton = downloadUrl
        ? `<a href="${escapeHtml(downloadUrl)}"
              style="padding:0.5rem 1.5rem;
                     background:#0066cc;color:white;
                     border-radius:4px;text-decoration:none;">
               📥 JT-Datei herunterladen
           </a>`
        : '';

    placeholder.innerHTML = `
        <div style="display:flex;flex-direction:column;
                    align-items:center;gap:0.75rem;
                    color:rgba(255,255,255,0.8);
                    max-width:440px;text-align:center;">
            <div style="font-size:3rem;">🔧</div>
            <p style="margin:0;font-weight:600;font-size:1rem;">
                JT-Konverter nicht verfügbar
            </p>
            <p style="margin:0;font-size:0.88rem;
                      color:rgba(255,255,255,0.6);">
                Die Datei <strong>${escapeHtml(fileName)}</strong>
                ist eine JT-Datei und benötigt einen serverseitigen
                Konverter zur 3D-Anzeige.
            </p>
            <div style="background:rgba(255,193,7,0.15);
                        border:1px solid rgba(255,193,7,0.4);
                        border-radius:6px;padding:0.75rem 1rem;
                        font-size:0.82rem;color:rgba(255,220,100,0.9);
                        text-align:left;width:100%;">
                <strong>Konfiguration:</strong><br>
                ${pathHint}
            </div>
            ${dlButton}
        </div>`;
}

/**
 * Zeigt eine Warnung in der Info-Bar wenn ein Fallback-GLB geladen wurde.
 * Bestehende Warnungen werden vorher entfernt (kein Doppel-Append).
 *
 * @param {string} fileName    - Originaldateiname
 * @param {string} downloadUrl - Download-URL
 */
function showFallbackWarning(fileName, downloadUrl) {
    // Vorherige Warnungen entfernen
    clearFallbackWarnings();

    const infoBar = document.querySelector('.viewer-info-bar');
    if (!infoBar) return;

    const warn = document.createElement('span');
    warn.className         = 'viewer-fallback-warning';  // für clearFallbackWarnings()
    warn.style.cssText     =
        'color:#ffc107;font-size:0.78rem;display:flex;' +
        'align-items:center;gap:0.4rem;';
    warn.innerHTML =
        `⚠ Fallback-Würfel (JT nicht konvertiert) – ` +
        `<a href="${escapeHtml(downloadUrl || '')}"
            style="color:#ffd54f;text-decoration:underline;"
            title="JT-Originaldatei herunterladen">
             Original herunterladen
         </a>`;

    infoBar.appendChild(warn);
}

/** Entfernt alle Fallback-Warnungen aus der Info-Bar. */
function clearFallbackWarnings() {
    document.querySelectorAll('.viewer-fallback-warning')
            .forEach(el => el.remove());
}

function showNotViewable(fileName, category) {
    hideLoading();
    canvas.style.display      = 'none';
    placeholder.style.display = 'flex';
    placeholder.innerHTML     = `
        <div style="display:flex;flex-direction:column;
                    align-items:center;gap:0.5rem;
                    color:rgba(255,255,255,0.6);">
            <div style="font-size:3rem;">📄</div>
            <p style="margin:0;font-size:1rem;">
                ${escapeHtml(fileName || '')}
            </p>
            <p style="margin:0;font-size:0.85rem;
                      color:rgba(255,255,255,0.4);">
                Kategorie "${escapeHtml(category || 'Keine')}"
                ist nicht viewer-kompatibel.<br>
                Nutze den Download-Button.
            </p>
        </div>`;
}

function updateInfoBar(element) {
    const fn  = element.dataset.fileName || '';
    const cat = element.dataset.category || '';

    const infoFileName = document.getElementById('infoFileName');
    const infoCategory = document.getElementById('infoCategory');
    const infoPolycount= document.getElementById('infoPolycount');
    const infoView     = document.getElementById('infoView');

    if (infoFileName)  infoFileName.textContent  = '📄 ' + fn;
    if (infoCategory)  infoCategory.textContent  =
        cat ? 'Kategorie: ' + cat : '';
    if (infoPolycount) infoPolycount.textContent = '';
    if (infoView)      infoView.textContent      =
        'Ansicht: ' + currentView.charAt(0).toUpperCase()
        + currentView.slice(1);
}

function escapeHtml(str) {
    if (!str) return '';
    return String(str)
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;');
}

// ══════════════════════════════════════════════════════════════
// RENDER-LOOP
// ══════════════════════════════════════════════════════════════

function animate() {
    requestAnimationFrame(animate);
    controls?.update();
    if (renderer && scene && camera) renderer.render(scene, camera);
}

function onWindowResize() {
    if (!renderer || !camera) return;
    const wrapper = canvas.parentElement;
    if (!wrapper) return;
    const w = wrapper.clientWidth;
    const h = Math.max(wrapper.clientHeight - 88, 200);
    camera.aspect = w / h;
    camera.updateProjectionMatrix();
    renderer.setSize(w, h);
}

// ══════════════════════════════════════════════════════════════
// START
// ══════════════════════════════════════════════════════════════

document.addEventListener('DOMContentLoaded', initThree);