(() => {
    'use strict';
    const list = document.getElementById('planning-list');
    const detail = document.getElementById('planning-detail');
    if (!list && !detail) return;
    const byId = id => document.getElementById(id);
    byId('pepperoni-chat-toggle')?.setAttribute('aria-label', 'SEPChat');
    const message = (text, error = false) => {
        const target = byId('planning-message');
        target.hidden = !text; target.textContent = text; target.dataset.error = String(error);
    };
    const request = async (url, method = 'GET', body) => {
        const headers = { Accept: 'application/json' };
        if (method !== 'GET') headers[document.querySelector('meta[name="_csrf_header"]').content] = document.querySelector('meta[name="_csrf"]').content;
        if (body && !(body instanceof FormData)) headers['Content-Type'] = 'application/json';
        const response = await fetch(url, { method, headers, credentials: 'same-origin', body: body instanceof FormData ? body : body ? JSON.stringify(body) : undefined });
        if (response.redirected || !response.headers.get('content-type')?.includes('application/json')) throw new Error('Sitzung abgelaufen oder Zugriff verweigert. Bitte neu anmelden.');
        const data = await response.json();
        if (!response.ok) throw new Error(data.message || data.detail || (response.status === 409 ? 'Projekt wurde geaendert. Bitte neu laden.' : `Anfrage fehlgeschlagen (${response.status}).`));
        return data;
    };
    if (list) {
        byId('create-project')?.addEventListener('submit', async event => {
            event.preventDefault();
            const button = event.target.querySelector('button'); button.disabled = true;
            try {
                const result = await request(list.dataset.base, 'POST', { name: byId('project-name').value });
                location.href = `${list.dataset.base}/${result.project.id}`;
            } catch (error) { message(error.message, true); button.disabled = false; }
        });
        return;
    }
    let view;
    let tab = 'structure';
    let rows = [];
    let dirty = false;
    let busy = false;
    let page = 0;
    const pageSize = 50;
    const canWrite = detail.dataset.write === 'true';
    const canDelete = detail.dataset.delete === 'true';
    const canExecute = detail.dataset.execute === 'true';
    const isCurrent = () => view.project.revision === Math.max(...view.revisions.map(revision => revision.number));
    const editable = () => canWrite && isCurrent() && view.project.status === 'WORKING' && !busy;
    const columnName = index => {
        let name = ''; let remaining = index + 1;
        while (remaining > 0) { remaining--; name = String.fromCharCode(65 + remaining % 26) + name; remaining = Math.floor(remaining / 26); }
        return name;
    };
    const discard = () => !dirty || confirm('Ungespeicherte Aenderungen verwerfen?');
    const tableRows = () => tab === 'structure' ? view.data.structure : tab === 'articles' ? view.articleRows
        : tab === 'workplaces' ? view.workplaceRows : view.data[tab];
    const tableHeaders = () => tab === 'structure' ? view.data.inputHeaders : tab === 'articles' ? view.articleHeaders
        : tab === 'workplaces' ? view.workplaceHeaders : tab === 'zones' ? view.zoneHeaders : view.outputHeaders;
    const loadRows = () => {
        rows = ['processes', 'reports'].includes(tab) ? [] : structuredClone(tableRows());
        dirty = false; page = 0; byId('table-search').value = '';
    };
    const updateDirty = () => {
        byId('dirty-status').textContent = dirty ? 'Ungespeicherte Aenderungen' : '';
        byId('discard-changes').hidden = !dirty || busy;
        byId('save-table').disabled = !dirty || busy;
    };
    const markDirty = () => { dirty = true; updateDirty(); };
    const adopt = result => { view = result; loadRows(); render(); };
    const execute = async (operation, success) => {
        if (busy) return;
        busy = true; render(); message('Wird verarbeitet ...');
        try { adopt(await operation()); message(success); }
        catch (error) { message(error.message, true); }
        finally { busy = false; render(); }
    };
    const part = (number, revision = '') => {
        const matches = view.articles.filter(candidate => candidate.number === number);
        return matches.find(candidate => candidate.revision === revision) || (matches.length === 1 ? matches[0] : null);
    };
    const enrichZones = () => {
        for (const row of rows) {
            const revisions = view.articles.filter(candidate => candidate.number === row.D);
            const selected = part(row.D, row.G) || (revisions.length === 1 ? revisions[0] : null);
            row.E = revisions.length > 1 ? 'WAHR' : 'FALSCH';
            row.F = [...new Set(revisions.map(candidate => candidate.revision))].sort().join(', ');
            row.H = selected ? selected.number + (selected.revision ? '_' + selected.revision : '') : '';
            row.I = selected?.description || '';
            if (!row.J) row.J = String(selected?.count || 1);
            row.Q = rows.find(candidate => candidate.A === row.P)?.B || '';
        }
    };
    const enrichOutput = row => {
        const zone = view.data.zones.find(candidate => candidate.B === row.A && candidate.D === row.B && candidate.G === row.E)
            || view.data.zones.find(candidate => candidate.B === row.A && candidate.D === row.B);
        row.C = zone?.J || ''; row.F = zone?.I || '';
    };
    const wouldCycle = (source, target, current) => {
        const pending = [target]; const visited = new Set();
        while (pending.length) {
            const number = pending.pop();
            if (number === source) return true;
            if (visited.has(number)) continue;
            visited.add(number);
            for (const row of rows) if (row !== current && row.A === number && row.P) pending.push(row.P);
        }
        return false;
    };
    const choices = (row, column) => {
        if (tab === 'zones' && column === 'D') {
            return [...new Map(view.articles.map(candidate => [candidate.number, candidate])).values()].map(candidate => [candidate.number, `${candidate.number} | ${candidate.description}`]);
        }
        if (tab === 'zones' && column === 'G') {
            const used = new Set(rows.filter(candidate => candidate !== row && candidate.D === row.D).map(candidate => candidate.G));
            return view.articles.filter(candidate => candidate.number === row.D && (!used.has(candidate.revision) || candidate.revision === row.G))
                .map(candidate => [candidate.revision, candidate.revision || '(ohne Revision)']);
        }
        if (tab === 'zones' && column === 'P') return [...new Map(rows.filter(candidate => candidate.A && candidate.B && candidate.A !== row.A && !wouldCycle(row.A, candidate.A, row)).map(candidate => [candidate.A, candidate.B])).entries()].map(([number, name]) => [number, `${number} | ${name}`]);
        if (tab === 'output' && column === 'A') return [...new Set(view.data.zones.map(zone => zone.B))].map(name => [name, name]);
        if (tab === 'output' && column === 'B') return view.data.zones.filter(zone => zone.B === row.A).map(zone => [zone.D, `${zone.D} | ${zone.I}`]);
        if (tab === 'output' && column === 'E') return view.data.zones.filter(zone => zone.B === row.A && zone.D === row.B).map(zone => [zone.G, zone.G || '(ohne Revision)']);
        if (tab === 'output' && column === 'J') return [['WAHR', 'WAHR'], ['FALSCH', 'FALSCH']];
        if (tab === 'workplaces' && column === 'A') return [...new Map(view.data.zones.map(zone => [zone.A, zone.B])).entries()].map(([number, name]) => [number, `${number} | ${name}`]);
        return null;
    };
    const numeric = { zones: new Set(['J', 'K', 'L', 'M', 'N', 'O', 'T', 'U', 'V', 'W', 'X', 'Y', 'AA', 'AB', 'AC', 'AE', 'AF', 'AG']), workplaces: new Set(['D']), output: new Set(['N', 'O', 'R', 'S', 'AB', 'AC', 'AJ', 'AP', 'AQ']) };
    const readOnly = { zones: new Set(['E', 'F', 'H', 'I', 'Q']), workplaces: new Set(['E']), articles: new Set(['F', 'G']), output: new Set(['C', 'F']) };
    const setOptions = (control, options, value) => {
        control.replaceChildren(...(tab === 'output' && control.dataset.column === 'J' ? [] : [new Option('', '')]));
        for (const [number, label] of options) control.append(new Option(label, number));
        if (value && !options.some(([number]) => number === value)) control.append(new Option(`${value} (ungueltig)`, value));
        control.value = value || '';
    };
    const refreshDependentControls = changedColumn => {
        for (const line of byId('planning-table').querySelectorAll('tbody tr[data-row-index]')) {
            const row = rows[Number(line.dataset.rowIndex)];
            for (const control of line.querySelectorAll('[data-column]')) {
                const column = control.dataset.column;
                if (control.tagName === 'SELECT') {
                    if (tab === 'zones' && ['A', 'B'].includes(changedColumn) && column !== 'P') continue;
                    setOptions(control, choices(row, column), row[column]);
                } else if (readOnly[tab].has(column) || (tab === 'zones' && ['H', 'I', 'J'].includes(column))) control.value = row[column] || '';
                control.title = row[column] || control.getAttribute('aria-label');
            }
        }
    };
    const renderCell = (row, column, title) => {
        const options = choices(row, column);
        const control = document.createElement(options ? 'select' : 'input');
        control.dataset.column = column;
        control.setAttribute('aria-label', `${column}: ${title}`);
        control.title = row[column] || title;
        if (options) {
            setOptions(control, options, row[column]);
            control.disabled = !editable();
        } else {
            control.type = numeric[tab]?.has(column) ? 'number' : 'text';
            if (control.type === 'number') { control.step = 'any'; control.min = '0'; }
            control.maxLength = 4000; control.readOnly = !editable() || readOnly[tab].has(column);
        }
        control.value = row[column] || '';
        const update = () => {
            row[column] = control.value; control.title = control.value;
            markDirty();
            if (tab === 'zones') {
                if (column === 'D') row.G = '';
                enrichZones();
            } else if (tab === 'output') {
                if (column === 'A') {
                    const zoneAssemblies = view.data.zones.filter(zone => zone.B === row.A);
                    row.B = zoneAssemblies.length === 1 ? zoneAssemblies[0].D : '';
                    row.E = zoneAssemblies.length === 1 ? zoneAssemblies[0].G : '';
                }
                enrichOutput(row);
            } else if (tab === 'articles') {
                row.F = row.B && row.C && row.E ? 'VOLLSTAENDIG' : 'FEHLENDE_STAMMDATEN';
            } else if (tab === 'workplaces' && column === 'A') {
                row.E = view.data.zones.find(zone => zone.A === row.A)?.B || '';
            }
            if (['A', 'B', 'C', 'D', 'E', 'G', 'P', 'J'].includes(column)) refreshDependentControls(column);
        };
        if (!options) control.addEventListener('input', update);
        if (options) control.addEventListener('change', update);
        return control;
    };
    const renderTable = () => {
        if (!view) return;
        if (['processes', 'reports'].includes(tab)) return;
        const table = byId('planning-table'); const head = table.tHead; const body = table.tBodies[0];
        if (tab === 'processes') return;
        head.replaceChildren(); body.replaceChildren();
        const headers = tableHeaders();
        const header = document.createElement('tr');
        const numberHeader = document.createElement('th'); numberHeader.textContent = '#'; numberHeader.className = 'planning-row-number'; header.append(numberHeader);
        headers.forEach((title, index) => {
            const cell = document.createElement('th'); cell.scope = 'col';
            const letter = document.createElement('span'); letter.className = 'planning-column-letter'; letter.textContent = columnName(index);
            cell.append(letter, document.createTextNode(title)); header.append(cell);
        });
        head.append(header);
        const query = byId('table-search').value.toLocaleLowerCase();
        const structureRows = tab === 'structure' ? rows : [];
        const zoneFilter = byId('structure-zone-filter').value;
        const targetFilter = byId('structure-target-filter').value;
        const relevantRows = new Set();
        if (tab === 'structure' && (zoneFilter || targetFilter)) {
            const targets = view.data.zones.filter(zone => (!zoneFilter || zone.A === zoneFilter) && (!targetFilter || `${zone.D}|${zone.G}` === targetFilter));
            const byParent = new Map();
            for (const candidate of structureRows) if (candidate.parentRow != null) {
                const list = byParent.get(candidate.parentRow) || []; list.push(candidate); byParent.set(candidate.parentRow, list);
            }
            const addDescendants = candidate => { relevantRows.add(candidate.sourceRow); for (const child of byParent.get(candidate.sourceRow) || []) addDescendants(child); };
            for (const zone of targets) for (const root of structureRows.filter(candidate => candidate.cells.J === zone.D && candidate.cells.M === zone.G)) addDescendants(root);
        }
        const filtered = rows.map((row, index) => ({ row, index })).filter(({ row }) => {
            const passesSearch = Object.values(tab === 'structure' ? row.cells : row).some(value => String(value).toLocaleLowerCase().includes(query));
            return passesSearch && (tab !== 'structure' || (!zoneFilter && !targetFilter) || relevantRows.has(row.sourceRow));
        });
        const totalPages = Math.max(1, Math.ceil(filtered.length / pageSize)); page = Math.min(page, totalPages - 1);
        for (const { row, index } of filtered.slice(page * pageSize, (page + 1) * pageSize)) {
            const line = document.createElement('tr');
            line.dataset.rowIndex = index;
            const number = document.createElement('td'); number.className = 'planning-row-number';
            number.textContent = tab === 'structure' ? row.sourceRow : index + 1;
            if (tab !== 'structure' && editable() && canDelete) {
                const remove = document.createElement('button'); remove.className = 'planning-icon'; remove.type = 'button'; remove.title = `Zeile ${index + 1} loeschen`; remove.setAttribute('aria-label', remove.title);
                const icon = document.createElement('i'); icon.className = 'a-icon boschicon-bosch-ic-delete'; remove.append(icon);
                remove.addEventListener('click', () => {
                    if (!confirm(`Zeile ${index + 1} loeschen?`)) return;
                    rows.splice(index, 1);
                    if (tab === 'zones') {
                        for (const candidate of rows) if (candidate.P && !rows.some(zone => zone.A === candidate.P)) candidate.P = '';
                        enrichZones();
                    }
                    markDirty(); renderTable();
                }); number.append(remove);
            }
            line.append(number);
            headers.forEach((title, column) => {
                const cell = document.createElement('td');
                if (tab === 'structure') {
                    cell.textContent = row.cells[columnName(column)] || '';
                    if (column === 10) {
                        cell.style.paddingLeft = `${10 + row.depth * 12}px`;
                        const parent = document.createElement('small'); parent.className = 'planning-parent'; parent.textContent = `Ebene ${row.depth}${row.parentRow ? ' | Elternzeile ' + row.parentRow : ''}`; cell.append(parent);
                    }
                } else cell.append(renderCell(row, columnName(column), title));
                line.append(cell);
            }); body.append(line);
        }
        if (!filtered.length) {
            const line = document.createElement('tr'); const cell = document.createElement('td'); cell.colSpan = headers.length + 1; cell.className = 'planning-empty'; cell.textContent = query ? 'Keine passenden Zeilen.' : 'Keine Zeilen vorhanden.'; line.append(cell); body.append(line);
        }
        byId('row-range').textContent = `${filtered.length ? page * pageSize + 1 : 0}-${Math.min((page + 1) * pageSize, filtered.length)} von ${filtered.length}`;
        byId('previous-page').disabled = page === 0; byId('next-page').disabled = page + 1 >= totalPages;
    };
    const latestTemplate = templateId => view.processPlan.templates.filter(template => template.templateId === templateId)
        .sort((left, right) => right.revision - left.revision)[0];
    const templateRevision = (templateId, revision) => view.processPlan.templates.find(template =>
        template.templateId === templateId && template.revision === revision);
    const textNode = (tag, text, className) => {
        const element = document.createElement(tag); element.textContent = text;
        if (className) element.className = className;
        return element;
    };
    const renderProcessBalance = () => {
        const panel = byId('process-balance'); panel.replaceChildren();
        if (!view.processPlan.balance.length) {
            panel.append(textNode('p', 'Noch keine Arbeitsschritte einem Arbeitsplatz zugeordnet.'));
            return;
        }
        for (const load of view.processPlan.balance) {
            const item = document.createElement('article'); item.className = 'planning-balance-item';
            item.dataset.load = load.loadPercent > 100 ? 'over' : load.loadPercent >= 80 ? 'warning' : 'ok';
            item.append(textNode('h3', load.workplaceName));
            item.append(textNode('p', `${load.targetErpNumber || 'Ohne Zielbaugruppe'}${load.targetRevision ? ' / Rev. ' + load.targetRevision : ''}`));
            item.append(textNode('p', `Arbeitsinhalt: ${Number(load.workContentSeconds).toFixed(1)} s`));
            item.append(textNode('p', `Taktziel: ${Number(load.targetSeconds).toFixed(1)} s`));
            item.append(textNode('p', `Auslastung: ${Number(load.loadPercent).toFixed(1)}%`));
            panel.append(item);
        }
    };
    let editingUse = null;
    const input = (label, value, type = 'text', field = '') => {
        const wrapper = document.createElement('label'); wrapper.append(document.createTextNode(label));
        const control = document.createElement('input'); control.type = type; control.value = value ?? ''; control.dataset.field = field;
        if (type === 'number') { control.step = 'any'; if (field !== 'sequence') control.min = '0'; }
        wrapper.append(control); return wrapper;
    };
    const select = (label, options, value, field) => {
        const wrapper = document.createElement('label'); wrapper.append(document.createTextNode(label));
        const control = document.createElement('select'); control.dataset.field = field;
        control.append(new Option('', ''));
        for (const option of options) control.append(new Option(option.label, option.value));
        control.value = value || ''; wrapper.append(control); return wrapper;
    };
    const makeResourceRow = resource => {
        const row = document.createElement('div'); row.className = 'planning-resource-row'; row.dataset.resourceId = resource?.id || crypto.randomUUID();
        const type = select('Typ', [{value:'MATERIAL',label:'Material'},{value:'TOOL',label:'Werkzeug'},{value:'AID',label:'Hilfsmittel'}], resource?.type || 'MATERIAL', 'type');
        const article = input('Artikelnummer', resource?.articleNumber || '', 'text', 'articleNumber');
        const articleRevision = input('Artikelrevision', resource?.articleRevision || '', 'text', 'articleRevision');
        const specialNumber = input('Spezialnummer', resource?.specialNumber || '', 'text', 'specialNumber');
        const description = input('Beschreibung', resource?.description || '', 'text', 'description');
        const quantity = input('Menge', resource?.quantity ?? '', 'number', 'quantity');
        const unit = input('Einheit', resource?.unit || '', 'text', 'unit');
        const containerType = input('Containertyp', resource?.containerType || '', 'text', 'containerType');
        const capacity = input('Einheiten je Container', resource?.unitsPerContainer ?? '', 'number', 'unitsPerContainer');
        const packagingUnit = input('Verpackungseinheit', resource?.packagingUnit || '', 'text', 'packagingUnit');
        const containerAssignment = input('Containerzuordnung', resource?.containerAssignment || '', 'text', 'containerAssignment');
        for (const element of [type, article, articleRevision, specialNumber, description, quantity, unit, containerType, capacity, packagingUnit, containerAssignment]) row.append(element);
        const remove = document.createElement('button'); remove.type = 'button'; remove.className = 'planning-icon'; remove.title = 'Ressource entfernen'; remove.setAttribute('aria-label', remove.title);
        const icon = document.createElement('i'); icon.className = 'a-icon boschicon-bosch-ic-delete'; remove.append(icon);
        remove.addEventListener('click', () => row.remove()); row.append(remove);
        return row;
    };
    const makeOperationDocumentRow = item => {
        const row = document.createElement('div'); row.className = 'planning-document-row'; row.dataset.documentId = item?.id || crypto.randomUUID();
        row.append(select('Dokumenttyp', [
            {value:'PROCESS_DESCRIPTION',label:'Prozessbeschreibung'}, {value:'DECISION',label:'Festlegung'},
            {value:'DRAWING',label:'Zeichnung'}, {value:'NOTE',label:'Notiz'}, {value:'OTHER',label:'Sonstiges'}
        ], item?.type || 'PROCESS_DESCRIPTION', 'type'));
        row.append(input('Titel', item?.title || '', 'text', 'title'));
        row.append(input('URL oder Dokumentpfad', item?.uri || '', 'text', 'uri'));
        row.append(select('Referenzart', [{value:'false',label:'Internes Dokument'}, {value:'true',label:'Externe Prozessvorgabe'}], String(Boolean(item?.external)), 'external'));
        row.append(input('Hinweis', item?.note || '', 'text', 'note'));
        const remove = document.createElement('button'); remove.type = 'button'; remove.className = 'planning-icon'; remove.title = 'Dokumentreferenz entfernen'; remove.setAttribute('aria-label', remove.title);
        const icon = document.createElement('i'); icon.className = 'a-icon boschicon-bosch-ic-delete'; remove.append(icon); remove.addEventListener('click', () => row.remove()); row.append(remove);
        return row;
    };
    const makeOperationCard = operation => {
        const card = document.createElement('article'); card.className = 'planning-operation-card'; card.dataset.operationId = operation?.id || crypto.randomUUID();
        const heading = document.createElement('div'); heading.className = 'planning-operation-card__heading';
        const title = textNode('h3', operation ? `Arbeitsschritt ${operation.sequence}` : 'Neuer Arbeitsschritt');
        heading.append(title);
        const reorder = document.createElement('div'); reorder.className = 'planning-operation-reorder';
        for (const [direction, label, iconName] of [['up', 'Arbeitsschritt nach oben', 'ui-ic-up'], ['down', 'Arbeitsschritt nach unten', 'ui-ic-down']]) {
            const move = document.createElement('button'); move.type = 'button'; move.className = 'planning-icon'; move.title = label; move.setAttribute('aria-label', label);
            const moveIcon = document.createElement('i'); moveIcon.className = `a-icon ${iconName}`; move.append(moveIcon);
            move.addEventListener('click', () => {
                const list = card.parentElement;
                const cards = [...list.querySelectorAll('.planning-operation-card')];
                const index = cards.indexOf(card); const other = cards[index + (direction === 'up' ? -1 : 1)];
                if (!other) return;
                const sequence = card.querySelector('[data-field="sequence"]');
                const otherSequence = other.querySelector('[data-field="sequence"]');
                [sequence.value, otherSequence.value] = [otherSequence.value, sequence.value];
                if (direction === 'up') list.insertBefore(card, other); else list.insertBefore(other, card);
                for (const candidate of list.querySelectorAll('.planning-operation-card')) {
                    const value = candidate.querySelector('[data-field="sequence"]').value;
                    candidate.querySelector('.planning-operation-card__heading h3').textContent = `Arbeitsschritt ${value}`;
                }
            });
            reorder.append(move);
        }
        heading.append(reorder);
        const remove = document.createElement('button'); remove.type = 'button'; remove.className = 'planning-icon'; remove.title = 'Arbeitsschritt entfernen'; remove.setAttribute('aria-label', remove.title);
        const icon = document.createElement('i'); icon.className = 'a-icon boschicon-bosch-ic-delete'; remove.append(icon); remove.addEventListener('click', () => card.remove());
        heading.append(remove); card.append(heading);
        const fields = document.createElement('div'); fields.className = 'planning-operation-fields';
        fields.append(input('Bezeichnung', operation?.name || '', 'text', 'name'));
        fields.append(input('Reihenfolge', operation?.sequence ?? 10, 'number', 'sequence'));
        fields.append(input('Ausführungen je Stück', operation?.executionsPerUnit ?? 1, 'number', 'executionsPerUnit'));
        fields.append(select('Arbeitsplatz', view.data.workplaces.map(workplace => ({value:workplace.id,label:`${workplace.code} · ${workplace.name}`})), operation?.workplaceId, 'workplaceId'));
        fields.append(input('Geschätzte Sekunden je Ausführung', operation?.estimatedSeconds ?? '', 'number', 'estimatedSeconds'));
        fields.append(input('Zeitstudie Sekunden je Ausführung', operation?.timeStudySeconds ?? '', 'number', 'timeStudySeconds'));
        fields.append(select('Zeitstudienquelle', [{value:'MTM',label:'MTM'},{value:'URS',label:'URS'},{value:'OTHER',label:'Sonstige'}], operation?.timeStudySource, 'timeStudySource'));
        fields.append(input('Zeitstudienreferenz', operation?.timeStudyReference || '', 'text', 'timeStudyReference'));
        fields.append(input('Istzeit / Messwert Sekunden', operation?.actualSeconds ?? '', 'number', 'actualSeconds'));
        const description = document.createElement('label'); description.append(document.createTextNode('Beschreibung'));
        const textarea = document.createElement('textarea'); textarea.rows = 2; textarea.dataset.field = 'description'; textarea.value = operation?.description || ''; description.append(textarea); fields.append(description);
        card.append(fields);
        const resources = document.createElement('div'); resources.className = 'planning-resource-editor';
        const resourceTitle = document.createElement('strong'); resourceTitle.textContent = 'Ressourcen dieses Arbeitsschritts'; resources.append(resourceTitle);
        const list = document.createElement('div'); list.className = 'planning-resource-rows';
        for (const resource of operation?.resources || []) list.append(makeResourceRow(resource));
        resources.append(list);
        const addResource = document.createElement('button'); addResource.type = 'button'; addResource.className = 'a-button a-button--secondary';
        addResource.append(textNode('span', 'Ressource hinzufügen', 'a-button__label')); addResource.addEventListener('click', () => list.append(makeResourceRow()));
        resources.append(addResource); card.append(resources);
        const documents = document.createElement('div'); documents.className = 'planning-resource-editor';
        documents.append(textNode('strong', 'Arbeitsfolgekarte: Dokumente und Vorgaben'));
        const documentList = document.createElement('div'); documentList.className = 'planning-document-list';
        for (const item of operation?.documents || []) documentList.append(makeOperationDocumentRow(item));
        documents.append(documentList);
        const addDocument = document.createElement('button'); addDocument.type = 'button'; addDocument.className = 'a-button a-button--secondary';
        addDocument.append(textNode('span', 'Dokumentreferenz hinzufügen', 'a-button__label'));
        addDocument.addEventListener('click', () => documentList.append(makeOperationDocumentRow()));
        documents.append(addDocument); card.append(documents);
        return card;
    };
    const openProcessEditor = use => {
        const template = templateRevision(use.templateId, use.templateRevision);
        if (!template) return;
        editingUse = use;
        byId('process-editor-title').textContent = `Prozess bearbeiten · ${template.name} R${String(template.revision).padStart(2, '0')}`;
        byId('process-template-name').value = template.name;
        const affected = view.processPlan.uses.filter(candidate => candidate.templateId === use.templateId && candidate.templateRevision === use.templateRevision);
        byId('process-usage-impact').textContent = affected.length
            ? `Diese Prozessrevision wird an ${affected.length} Verwendungsstelle(n) genutzt. Vor dem Speichern wählst du die betroffenen Verwendungen.`
            : 'Diese Vorlage ist noch keiner Planung zugeordnet. Die Bearbeitung erstellt eine neue Vorlagenrevision.';
        const localScope = byId('process-editor-form').querySelector('[name="change-scope"][value="LOCAL"]');
        const sharedScope = byId('process-editor-form').querySelector('[name="change-scope"][value="SHARED"]');
        localScope.disabled = affected.length === 0;
        sharedScope.checked = affected.length === 0;
        if (affected.length) localScope.checked = true;
        const checks = byId('process-affected-uses'); checks.replaceChildren();
        for (const candidate of affected) {
            const group = view.processPlan.groups.find(item => item.id === candidate.stepGroupId);
            const check = document.createElement('label'); check.className = 'planning-impact-use';
            const box = document.createElement('input'); box.type = 'checkbox'; box.value = candidate.id; box.checked = candidate.id === use.id; box.dataset.affectedUse = 'true';
            const target = candidate.targetErpNumber ? `${candidate.targetErpNumber}${candidate.targetRevision ? ' / Rev. ' + candidate.targetRevision : ''}` : 'ohne Zielbaugruppe';
            check.append(box, textNode('span', `${group?.name || 'Unbekannte Gruppe'} · ${target}`)); checks.append(check);
        }
        const operations = byId('process-operations'); operations.replaceChildren();
        for (const operation of [...template.operations].sort((left, right) => left.sequence - right.sequence)) operations.append(makeOperationCard(operation));
        byId('process-editor-dialog').showModal();
    };
    const renderProcesses = () => {
        if (!view) return;
        const consistency = byId('process-consistency-summary');
        const issues = view.processPlan.consistencyIssues || [];
        consistency.hidden = issues.length === 0;
        consistency.dataset.warning = String(issues.length > 0);
        consistency.textContent = issues.length ? `Prüfhinweise: ${issues.join(' ')}` : '';
        renderProcessBalance();
        const root = byId('process-groups'); root.replaceChildren();
        for (const group of [...view.processPlan.groups].sort((left, right) => left.sequence - right.sequence)) {
            const section = document.createElement('section'); section.className = 'planning-process-group';
            const groupHeader = document.createElement('div'); groupHeader.className = 'planning-process-card__header';
            groupHeader.append(textNode('h3', `${group.sequence}. ${group.name}`));
            if (editable()) {
                const actions = document.createElement('div'); actions.className = 'planning-toolbar';
                for (const [direction, label, iconName] of [['up', 'Nach oben', 'ui-ic-up'], ['down', 'Nach unten', 'ui-ic-down']]) {
                    const move = document.createElement('button'); move.type = 'button'; move.className = 'planning-icon'; move.title = label; move.setAttribute('aria-label', label);
                    const icon = document.createElement('i'); icon.className = `a-icon ${iconName}`; move.append(icon);
                    move.addEventListener('click', async () => execute(() => request(`${detail.dataset.base}/process-groups/${group.id}/move`, 'POST', { version: view.project.version, direction }), 'Schrittgruppe verschoben.'));
                    actions.append(move);
                }
                const rename = document.createElement('button'); rename.type = 'button'; rename.className = 'planning-icon'; rename.title = 'Schrittgruppe umbenennen'; rename.setAttribute('aria-label', rename.title);
                const editIcon = document.createElement('i'); editIcon.className = 'a-icon boschicon-bosch-ic-edit'; rename.append(editIcon);
                rename.addEventListener('click', async () => { const name = prompt('Neuer Name der Schrittgruppe', group.name); if (name?.trim()) await execute(() => request(`${detail.dataset.base}/process-groups/${group.id}`, 'PUT', { version: view.project.version, name }), 'Schrittgruppe umbenannt.'); });
                actions.append(rename);
                const remove = document.createElement('button'); remove.type = 'button'; remove.className = 'planning-icon'; remove.title = 'Schrittgruppe löschen'; remove.setAttribute('aria-label', remove.title);
                const deleteIcon = document.createElement('i'); deleteIcon.className = 'a-icon boschicon-bosch-ic-delete'; remove.append(deleteIcon);
                remove.addEventListener('click', async () => { if (confirm('Schrittgruppe loeschen? Zugeordnete Prozesse muessen zuerst entfernt werden.')) await execute(() => request(`${detail.dataset.base}/process-groups/${group.id}?version=${view.project.version}`, 'DELETE'), 'Schrittgruppe geloescht.'); });
                actions.append(remove); groupHeader.append(actions);
            }
            section.append(groupHeader);
            const uses = view.processPlan.uses.filter(use => use.stepGroupId === group.id);
            for (const use of uses) {
                const template = templateRevision(use.templateId, use.templateRevision); if (!template) continue;
                const summary = view.processPlan.templates.find(item => item.templateId === use.templateId && item.revision === use.templateRevision);
                const card = document.createElement('article'); card.className = 'planning-process-card';
                const header = document.createElement('header'); header.className = 'planning-process-card__header';
                const title = document.createElement('div'); title.append(textNode('h4', template.name));
                const target = use.targetErpNumber ? `${use.targetErpNumber}${use.targetRevision ? ' / Rev. ' + use.targetRevision : ''}` : 'Ohne Zielbaugruppe';
                title.append(textNode('span', `R${String(template.revision).padStart(2, '0')} · ${target} · ${summary?.usageCount || 0} Verwendung(en)`, 'planning-process-card__meta'));
                header.append(title);
                if (editable()) {
                    const edit = document.createElement('button'); edit.type = 'button'; edit.className = 'a-button a-button--secondary';
                    edit.append(textNode('span', 'Vorlage bearbeiten', 'a-button__label')); edit.addEventListener('click', () => openProcessEditor(use)); header.append(edit);
                    const remove = document.createElement('button'); remove.type = 'button'; remove.className = 'planning-icon'; remove.title = 'Prozessverwendung entfernen'; remove.setAttribute('aria-label', remove.title);
                    const icon = document.createElement('i'); icon.className = 'a-icon boschicon-bosch-ic-delete'; remove.append(icon);
                    remove.addEventListener('click', async () => {
                        if (confirm('Diese Prozessverwendung entfernen? Die Prozessvorlage bleibt erhalten.'))
                            await execute(() => request(`${detail.dataset.base}/process-uses/${use.id}?version=${view.project.version}`, 'DELETE'), 'Prozessverwendung entfernt.');
                    }); header.append(remove);
                }
                card.append(header);
                const table = document.createElement('table'); table.className = 'planning-step-table';
                const head = document.createElement('tr'); for (const value of ['Reihenfolge', 'Arbeitsschritt', 'Ausführungen je Stück', 'Schätzung', 'Zeitstudie', 'Effektive Zeit', 'Istzeit', 'Arbeitsplatz', 'Ressourcen', 'Dokumente']) head.append(textNode('th', value));
                const thead = document.createElement('thead'); thead.append(head); table.append(thead);
                const body = document.createElement('tbody');
                for (const operation of [...template.operations].sort((left, right) => left.sequence - right.sequence)) {
                    const row = document.createElement('tr'); row.append(textNode('td', String(operation.sequence)));
                    const description = document.createElement('td'); description.append(textNode('strong', operation.name));
                    if (operation.description) description.append(textNode('div', operation.description)); row.append(description);
                    const effective = operation.timeStudySeconds ?? operation.estimatedSeconds;
                    row.append(textNode('td', String(operation.executionsPerUnit)));
                    row.append(textNode('td', operation.estimatedSeconds == null ? '—' : `${Number(operation.estimatedSeconds).toFixed(1)} s`));
                    row.append(textNode('td', operation.timeStudySeconds == null ? '—' : `${Number(operation.timeStudySeconds).toFixed(1)} s · ${operation.timeStudySource}`));
                    row.append(textNode('td', effective == null ? 'Fehlt' : `${Number(effective).toFixed(1)} s · ${operation.timeStudySeconds != null ? 'Zeitstudie' : 'Schätzung'}`));
                    row.append(textNode('td', operation.actualSeconds == null ? '—' : `${Number(operation.actualSeconds).toFixed(1)} s`));
                    const workplace = view.data.workplaces.find(item => item.id === operation.workplaceId);
                    row.append(textNode('td', workplace ? `${workplace.code} · ${workplace.name}` : 'Nicht zugeordnet'));
                    const resourceCell = document.createElement('td'); const resourceList = document.createElement('ul'); resourceList.className = 'planning-resource-list';
                    for (const resource of operation.resources || []) {
                        const part = resource.articleNumber ? `${resource.articleNumber}${resource.articleRevision ? ' / Rev. ' + resource.articleRevision : ''}` : '';
                        const detail = [part, resource.description].filter(Boolean).join(' · ');
                        resourceList.append(textNode('li', `${resource.type}: ${detail} · ${resource.quantity ?? ''} ${resource.unit || ''}`));
                    }
                    if (!(operation.resources || []).length) resourceList.append(textNode('li', 'Keine Ressourcen'));
                    resourceCell.append(resourceList); row.append(resourceCell); body.append(row);
                    const documentCell = document.createElement('td');
                    for (const item of operation.documents || []) {
                        const link = document.createElement(item.uri ? 'a' : 'span');
                        link.textContent = `${item.title} · ${item.type}`;
                        if (item.uri) { link.href = item.uri; link.target = '_blank'; link.rel = 'noopener noreferrer'; }
                        documentCell.append(link, document.createElement('br'));
                    }
                    if (!(operation.documents || []).length) documentCell.textContent = 'Keine Dokumente';
                    row.append(documentCell);
                }
                table.append(body); card.append(table); section.append(card);
            }
            if (!uses.length) section.append(textNode('p', 'Noch keine Prozesse dieser Schrittgruppe zugeordnet.'));
            root.append(section);
        }
        if (!view.processPlan.groups.length) root.append(textNode('p', 'Lege eine Schrittgruppe an, um den Prozessablauf zu strukturieren.'));
        const usedTemplates = new Set(view.processPlan.uses.map(use => `${use.templateId}\u0000${use.templateRevision}`));
        const unusedTemplates = view.processPlan.templates.filter(template => !usedTemplates.has(`${template.templateId}\u0000${template.revision}`));
        if (unusedTemplates.length) {
            const section = document.createElement('section'); section.className = 'planning-process-group'; section.append(textNode('h3', 'Noch nicht zugeordnete Prozessvorlagen'));
            for (const template of unusedTemplates) {
                const line = document.createElement('p'); line.append(textNode('strong', `${template.name} · R${template.revision}`));
                line.append(document.createTextNode(` · ${template.operations.length} Arbeitsschritt(e) `));
                const edit = document.createElement('button'); edit.type = 'button'; edit.className = 'a-button a-button--secondary'; edit.append(textNode('span', 'Arbeitsschritte bearbeiten', 'a-button__label'));
                edit.addEventListener('click', () => openProcessEditor({ templateId: template.templateId, templateRevision: template.revision, id: '' }));
                line.append(edit);
                if (editable() && canDelete) {
                    const remove = document.createElement('button'); remove.type = 'button'; remove.className = 'planning-icon'; remove.title = 'Unbenutzte Vorlage löschen'; remove.setAttribute('aria-label', remove.title);
                    const icon = document.createElement('i'); icon.className = 'a-icon boschicon-bosch-ic-delete'; remove.append(icon);
                    remove.addEventListener('click', async () => {
                        if (confirm('Unbenutzte Prozessvorlage und alle Revisionen loeschen?'))
                            await execute(() => request(`${detail.dataset.base}/process-templates/${template.templateId}?version=${view.project.version}`, 'DELETE'), 'Prozessvorlage geloescht.');
                    });
                    line.append(remove);
                }
                section.append(line);
            }
            root.append(section);
        }
    };
    const fillReportFilters = () => {
        const zone = byId('report-zone'); const oldZone = zone.value; zone.replaceChildren(new Option('Alle Zonen', ''));
        for (const [number, name] of new Map(view.data.zones.map(row => [row.A, row.B]))) zone.append(new Option(`${number} | ${name}`, number));
        zone.value = oldZone;
        const workplace = byId('report-workplace'); const oldWorkplace = workplace.value; workplace.replaceChildren(new Option('Alle Arbeitsplätze', ''));
        for (const row of view.data.workplaces) workplace.append(new Option(`${row.code} | ${row.name}`, row.id));
        workplace.value = oldWorkplace;
        const target = byId('report-target'); const oldTarget = target.value; target.replaceChildren(new Option('Alle Zielbaugruppen', ''));
        for (const article of view.articles) target.append(new Option(`${article.number}${article.revision ? ' / Rev. ' + article.revision : ''} | ${article.description}`, `${article.number}\u0000${article.revision}`));
        target.value = oldTarget;
    };
    const reportTitle = key => ({customerErpNumber:'Kunden-ERP-Nr.',customerRevision:'Kundenrevision',boschSapNumber:'Bosch-SAP-Nr.',boschSapRevision:'SAP-Revision',details:'Prüfhinweis',
        targetErpNumber:'Ziel-ERP-Nr.',targetRevision:'Zielrevision',articleNumber:'Artikelnummer',articleRevision:'Artikelrevision',
        expectedQuantity:'Erwartete Menge je Stück',actualQuantity:'Verbrauch je Stück',variance:'Abweichung',status:'Status',description:'Beschreibung',
        suppliedPerHour:'Angebot/Stunde',requiredPerHour:'Bedarf/Stunde',variancePerHour:'Abweichung/Stunde',sourceZoneName:'Quellzone',
        destinationZoneName:'Zielzone',sourceTaktSeconds:'Quelltakt (s)',destinationTaktSeconds:'Zieltakt (s)',resourceType:'Ressourcentyp',
        specialNumber:'Spezialnummer',processName:'Prozess',operationId:'Arbeitsschritt-ID',operationName:'Arbeitsschritt',containerAssignment:'Containerzuordnung',
        workplaceId:'Arbeitsplatz-ID',workplaceName:'Arbeitsplatz',requiredQuantityPerUnit:'Bedarf je Stück',containerType:'Containertyp',
        unitsPerContainer:'Einheiten/Container',containersPerUnit:'Container je Stück',packagingUnit:'Verpackungseinheit',unit:'Einheit'})[key] || key;
    const renderReport = report => {
        const rows = report.rows || [];
        const table = byId('report-table'); table.tHead.replaceChildren(); table.tBodies[0].replaceChildren();
        if (!rows.length) {
            const header = document.createElement('tr'); const cell = document.createElement('th'); cell.textContent = 'Keine Reportzeilen'; header.append(cell); table.tHead.append(header);
            const body = document.createElement('tr'); const empty = document.createElement('td'); empty.textContent = 'Für die gewählten Filter liegen keine Daten vor.'; body.append(empty); table.tBodies[0].append(body);
            byId('report-summary').textContent = '0 Ergebnisse'; return;
        }
        const keys = Object.keys(rows[0]); const head = document.createElement('tr');
        for (const key of keys) { const cell = document.createElement('th'); cell.textContent = reportTitle(key); head.append(cell); }
        table.tHead.append(head);
        for (const row of rows) {
            const tr = document.createElement('tr');
            for (const key of keys) {
                const td = document.createElement('td'); const value = row[key];
                td.textContent = value == null ? '' : typeof value === 'object' ? JSON.stringify(value) : String(value);
                if (key === 'status') td.dataset.status = String(value).toLowerCase();
                tr.append(td);
            }
            table.tBodies[0].append(tr);
        }
        const counts = rows.reduce((result, row) => { result[row.status] = (result[row.status] || 0) + 1; return result; }, {});
        byId('report-summary').textContent = `${rows.length} Ergebnisse${Object.entries(counts).filter(([status]) => status !== 'undefined').map(([status, count]) => ` · ${status}: ${count}`).join('')}`;
    };
    const runReport = async () => {
        const params = new URLSearchParams({ kind: byId('report-kind').value, revision: String(view.project.revision) });
        if (byId('report-zone').value) params.set('zone', byId('report-zone').value);
        if (byId('report-workplace').value) params.set('workplace', byId('report-workplace').value);
        if (byId('report-target').value) {
            const [number, revision] = byId('report-target').value.split('\u0000');
            params.set('target', number); params.set('targetRevision', revision);
        }
        const kind = byId('report-kind').value;
        const supportsState = ['validation', 'zone-supply', 'numbers'].includes(kind);
        byId('report-state-label').hidden = !supportsState; byId('report-state').hidden = !supportsState;
        if (supportsState && byId('report-state').value) params.set('state', byId('report-state').value);
        byId('run-report').disabled = true; byId('report-summary').textContent = 'Report wird berechnet …';
        try {
            const report = await request(`${detail.dataset.base}/reports?${params}`);
            renderReport(report);
            byId('download-report').href = `${detail.dataset.base}/reports.xlsx?${params}`;
            byId('download-report').hidden = false;
        } catch (error) { byId('report-summary').textContent = error.message; byId('download-report').hidden = true; }
        finally { byId('run-report').disabled = false; }
    };
    const render = () => {
        if (!view) return;
        byId('planning-title').textContent = view.project.name;
        document.title = `${view.project.name} | Fertigungsplanung`;
        const status = byId('project-status'); status.textContent = view.project.status; status.dataset.status = view.project.status;
        const revisionSelect = byId('revision-select'); revisionSelect.replaceChildren();
        for (const revision of view.revisions) revisionSelect.append(new Option(`R${String(revision.number).padStart(2, '0')} | ${revision.status}`, revision.number));
        revisionSelect.value = view.project.revision; revisionSelect.disabled = busy;
        const revision = view.revisions.find(candidate => candidate.number === view.project.revision);
        byId('planning-audit').textContent = revision.releasedBy ? `Freigegeben: ${revision.releasedBy} | ${new Date(revision.releasedAt).toLocaleString('de-DE')}` : `Geaendert: ${view.project.modifiedBy} | ${new Date(view.project.modifiedAt).toLocaleString('de-DE')}`;
        byId('export-project').href = `${detail.dataset.base}/export?revision=${view.project.revision}&numbers=${byId('number-mode').value}`;
        byId('release-project').hidden = !editable() || !canExecute;
        const breakdownConflicts = view.data.assemblyDefinitions.filter(assembly => assembly.conflictingBreakdown);
        byId('release-project').disabled = dirty || !view.data.structure.length || !view.data.output.length || breakdownConflicts.length > 0
            || (view.processPlan.consistencyIssues || []).length > 0;
        byId('revise-project').hidden = !canWrite || !canExecute || !isCurrent() || busy;
        byId('work-sequence-export').hidden = view.project.status !== 'RELEASED';
        byId('work-sequence-export').href = `${detail.dataset.base}/work-sequence.xlsx?revision=${view.project.revision}`;
        for (const name of ['structure', 'zones', 'output']) byId(`count-${name}`).textContent = view.data[name].length;
        byId('count-articles').textContent = view.articleRows.length;
        byId('count-workplaces').textContent = view.workplaceRows.length;
        byId('count-processes').textContent = view.processPlan.uses.length;
        byId('import-structure').hidden = tab !== 'structure' || !editable();
        const compare = byId('structure-compare-controls');
        compare.hidden = tab !== 'structure' || view.revisions.length < 2;
        const previousRevision = byId('compare-previous-revision');
        const selectedPrevious = previousRevision.value;
        previousRevision.replaceChildren(...view.revisions.filter(item => item.number !== view.project.revision)
            .map(item => new Option(`R${item.number} · ${item.status}`, item.number)));
        if (view.revisions.some(item => String(item.number) === selectedPrevious && item.number !== view.project.revision)) previousRevision.value = selectedPrevious;
        else if (previousRevision.options.length) previousRevision.selectedIndex = 0;
        byId('add-article').hidden = tab !== 'articles' || !editable();
        byId('add-process-group').hidden = tab !== 'processes' || !editable();
        byId('add-process-template').hidden = tab !== 'processes' || !editable();
        byId('add-process-use').hidden = tab !== 'processes' || !editable();
        byId('copy-process-plan').hidden = tab !== 'processes' || !editable();
        byId('copy-process-plan').disabled = !view.processPlan.uses.some(use => use.targetErpNumber);
        byId('add-process-use').disabled = !view.processPlan.groups.length || !view.processPlan.templates.some(template => template.revision === template.latestRevision);
        byId('add-row').hidden = !['zones', 'workplaces', 'output'].includes(tab) || !editable();
        byId('add-row').disabled = tab === 'zones' ? !view.articles.length : tab === 'workplaces' ? !view.data.zones.length : !view.data.zones.length;
        byId('save-table').hidden = tab === 'structure' || tab === 'processes' || !editable();
        byId('planning-table-region').hidden = ['processes', 'reports'].includes(tab);
        byId('process-workbench').hidden = tab !== 'processes';
        byId('report-workbench').hidden = tab !== 'reports';
        byId('generate-output').hidden = tab !== 'output' || !editable();
        byId('generate-output').disabled = dirty || !view.data.zones.length;
        for (const filter of ['structure-zone-filter', 'structure-zone-label', 'structure-target-filter', 'structure-target-label']) filter.includes('label')
            ? (byId(filter).hidden = tab !== 'structure') : (byId(filter).hidden = tab !== 'structure');
        const zoneSelect = byId('structure-zone-filter'); const priorZone = zoneSelect.value; zoneSelect.replaceChildren(new Option('Alle Zonen', ''));
        for (const [number, name] of new Map(view.data.zones.map(zone => [zone.A, zone.B]))) zoneSelect.append(new Option(`${number} | ${name}`, number));
        zoneSelect.value = priorZone;
        const targetSelect = byId('structure-target-filter'); const priorTarget = targetSelect.value; targetSelect.replaceChildren(new Option('Alle Zielbaugruppen', ''));
        const selectedZone = zoneSelect.value;
        for (const zone of view.data.zones.filter(zone => !selectedZone || zone.A === selectedZone)) targetSelect.append(new Option(`${zone.D}_${zone.G} | ${zone.I}`, `${zone.D}|${zone.G}`));
        targetSelect.value = priorTarget;
        byId('source-info').textContent = tab === 'structure' && view.data.sourceFilename ? view.data.sourceFilename : '';
        byId('source-info').title = view.data.sourceSha256 || '';
        const integrity = byId('planning-integrity-summary');
        const missingMasters = view.data.articleMasters.filter(article => article.missingMasterData).length;
        const missingStructure = view.data.structure.length === 0;
        const processIssues = view.processPlan.consistencyIssues || [];
        integrity.hidden = breakdownConflicts.length === 0 && missingMasters === 0 && !missingStructure && processIssues.length === 0;
        integrity.dataset.warning = String(breakdownConflicts.length > 0 || processIssues.length > 0);
        integrity.textContent = [breakdownConflicts.length ? `${breakdownConflicts.length} wiederverwendete Baugruppe(n) haben abweichende Unterpositionen oder Mengen; vor Freigabe klaeren.` : '',
            missingMasters ? `${missingMasters} Artikelstamm/Staemme sind unvollstaendig (Revision, Beschreibung, SAP-Nummer oder widerspruechliche Importwerte).` : '',
            missingStructure ? 'Vorplanung ohne BOM: Zonen und Arbeitsplaetze koennen gepflegt werden; Freigabe setzt einen Strukturimport voraus.' : '',
            processIssues.length ? `Arbeitsfolgeprüfung: ${processIssues.join(' ')}` : ''].filter(Boolean).join(' ');
        const flow = byId('zone-flow-summary');
        flow.hidden = tab !== 'zones';
        if (tab === 'zones') {
            const flows = view.data.deliveries || [];
            flow.textContent = flows.length ? flows.map(delivery => {
                const source = view.data.zones.find(zone => zone.A === delivery.sourceZone);
                const destination = view.data.zones.find(zone => zone.A === delivery.destinationZone);
                return `${source?.B || delivery.sourceZone} liefert ${delivery.customerErpNumber}${delivery.customerRevision ? '_' + delivery.customerRevision : ''} (${delivery.quantity}) an ${destination?.B || delivery.destinationZone}`;
            }).join(' | ') : 'Noch keine zonenuebergreifenden Lieferungen konfiguriert.';
        }
        document.querySelectorAll('[data-tab]').forEach(button => { button.setAttribute('aria-selected', String(button.dataset.tab === tab)); button.disabled = busy; });
        byId('planning-panel').setAttribute('aria-labelledby', `tab-${tab}`);
        updateDirty(); renderTable();
        if (tab === 'processes') renderProcesses();
        if (tab === 'reports') fillReportFilters();
    };
    document.querySelectorAll('[data-tab]').forEach(button => button.addEventListener('click', () => {
        if (tab === button.dataset.tab || !discard()) return;
        tab = button.dataset.tab; loadRows(); message(''); render();
    }));
    byId('revision-select').addEventListener('change', async event => {
        const selected = event.target.value;
        if (!discard()) { event.target.value = view.project.revision; return; }
        await execute(() => request(`${detail.dataset.base}/data?revision=${selected}`), 'Revision geladen.');
    });
    byId('table-search').addEventListener('input', () => { page = 0; renderTable(); });
    byId('report-kind').addEventListener('change', () => {
        const supported = ['validation', 'zone-supply', 'numbers'].includes(byId('report-kind').value);
        byId('report-state-label').hidden = !supported; byId('report-state').hidden = !supported;
        if (!supported) byId('report-state').value = '';
    });
    byId('previous-page').addEventListener('click', () => { page--; renderTable(); });
    byId('next-page').addEventListener('click', () => { page++; renderTable(); });
    byId('structure-zone-filter').addEventListener('change', () => { byId('structure-target-filter').value = ''; renderTable(); });
    byId('structure-target-filter').addEventListener('change', renderTable);
    byId('run-report').addEventListener('click', runReport);
    byId('compare-structure').addEventListener('click', async () => {
        const previousRevision = byId('compare-previous-revision').value;
        if (!previousRevision) return;
        try {
            const comparison = await request(`${detail.dataset.base}/structure-comparison?previousRevision=${encodeURIComponent(previousRevision)}&currentRevision=${view.project.revision}`);
            const table = byId('structure-compare-table');
            const headers = ['Änderung', 'Ebene', 'ERP-Nr.', 'Revision', 'Beschreibung', 'Menge vorher', 'Menge aktuell', 'Excelzeile vorher', 'Excelzeile aktuell'];
            table.querySelector('thead').replaceChildren();
            const headerRow = document.createElement('tr');
            for (const label of headers) headerRow.append(textNode('th', label));
            table.querySelector('thead').append(headerRow);
            const body = table.querySelector('tbody'); body.replaceChildren();
            for (const change of comparison.rows) {
                const row = document.createElement('tr'); row.dataset.change = change.change;
                const values = [change.change, String(change.depth + 1), change.articleNumber, change.articleRevision,
                    change.description, change.previousQuantity, change.currentQuantity,
                    change.previousRow == null ? '' : String(change.previousRow), change.currentRow == null ? '' : String(change.currentRow)];
                for (const value of values) row.append(textNode('td', value || ''));
                body.append(row);
            }
            if (!comparison.rows.length) {
                const row = document.createElement('tr'); const cell = textNode('td', 'Keine Stücklistenänderungen zwischen diesen Revisionen.');
                cell.colSpan = headers.length; row.append(cell); body.append(row);
            }
            byId('structure-compare-dialog').showModal();
        } catch (error) { message(error.message, true); }
    });
    byId('add-article').addEventListener('click', () => {
        rows.push(Object.fromEntries(tableHeaders().map((title, index) => [columnName(index), ''])));
        byId('table-search').value = ''; page = Math.floor((rows.length - 1) / pageSize); markDirty(); renderTable();
    });
    byId('add-row').addEventListener('click', () => {
        const headers = tableHeaders();
        const row = Object.fromEntries(headers.map((title, index) => [columnName(index), '']));
        if (tab === 'output') row.J = 'FALSCH';
        rows.push(row); byId('table-search').value = ''; page = Math.floor((rows.length - 1) / pageSize);
        markDirty(); renderTable();
    });
    byId('discard-changes').addEventListener('click', () => { if (discard()) { loadRows(); render(); } });
    byId('save-table').addEventListener('click', async () => {
        if (tab === 'zones' && view.data.output.length && !confirm('Zonenaenderungen setzen die bestehende Fertigungsliste zurueck. Fortfahren?')) return;
        await execute(() => request(`${detail.dataset.base}/tables/${tab}`, 'PUT', { version: view.project.version, rows, confirmOutputClear: tab === 'zones' && view.data.output.length > 0 }), 'Tabelle gespeichert.');
    });
    byId('import-structure').addEventListener('submit', async event => {
        event.preventDefault();
        const file = byId('structure-file').files[0]; if (!file) return;
        if (file.size > 20 * 1024 * 1024) { message('Maximale Dateigroesse: 20 MB.', true); return; }
        const newVersion = byId('import-new-version').checked;
        const promptText = newVersion
            ? 'Eine neue Musterstücklisten-Version wird aus diesem Planungsstand erzeugt. Die alte Revision bleibt erhalten; die Fertigungsliste der neuen Version wird zurückgesetzt.'
            : 'Neuimport ersetzt die Struktur und entfernt Zonen und Fertigungsliste in dieser Arbeitsrevision. Fortfahren?';
        if ((view.data.structure.length || newVersion) && !confirm(promptText)) return;
        const body = new FormData(); body.append('file', file); body.append('version', view.project.version);
        body.append('confirmReplace', String(view.data.structure.length > 0));
        body.append('newVersion', String(newVersion));
        await execute(() => request(`${detail.dataset.base}/import`, 'POST', body), 'Struktur importiert.');
        byId('import-new-version').checked = false;
    });
    byId('generate-output').addEventListener('click', async () => {
        if (view.data.output.length && !confirm('Bestehende Fertigungsliste mit allen manuellen Aenderungen ersetzen?')) return;
        await execute(() => request(`${detail.dataset.base}/generate`, 'POST', { version: view.project.version, confirmReplace: view.data.output.length > 0 }), 'Fertigungsliste erzeugt.');
    });
    for (const action of ['release', 'revise']) byId(`${action}-project`).addEventListener('click', async () => {
        if (!confirm(action === 'release' ? 'Diese Revision verbindlich freigeben und schreibschuetzen?' : 'Neue Planungsversion aus diesem Stand erstellen?')) return;
        await execute(() => request(`${detail.dataset.base}/lifecycle/${action}`, 'POST', { version: view.project.version }), action === 'release' ? 'Revision freigegeben.' : 'Neue Arbeitsrevision erstellt.');
    });
    byId('number-mode').addEventListener('change', () => { byId('export-project').href = `${detail.dataset.base}/export?revision=${view.project.revision}&numbers=${byId('number-mode').value}`; });
    byId('add-process-group').addEventListener('click', async () => {
        const name = prompt('Name der Schrittgruppe'); if (!name?.trim()) return;
        await execute(() => request(`${detail.dataset.base}/process-groups`, 'POST', { version: view.project.version, name }), 'Schrittgruppe angelegt.');
    });
    byId('add-process-template').addEventListener('click', async () => {
        const name = prompt('Name des Prozesses / der logischen Station'); if (!name?.trim()) return;
        await execute(() => request(`${detail.dataset.base}/process-templates`, 'POST', { version: view.project.version, name }), 'Prozessvorlage angelegt.');
    });
    byId('add-process-use').addEventListener('click', () => {
        const groupSelect = byId('use-group'); groupSelect.replaceChildren();
        for (const group of view.processPlan.groups) groupSelect.append(new Option(`${group.sequence}. ${group.name}`, group.id));
        const templateSelect = byId('use-template'); templateSelect.replaceChildren();
        const templates = view.processPlan.templates.filter(template => template.revision === template.latestRevision);
        for (const template of templates) templateSelect.append(new Option(`${template.name} · R${template.revision}`, template.templateId));
        const targetSelect = byId('use-target'); targetSelect.replaceChildren(new Option('Noch keiner Zielbaugruppe zugeordnet', ''));
        for (const article of view.articles) targetSelect.append(new Option(`${article.number}${article.revision ? ' / Rev. ' + article.revision : ''} · ${article.description}`, `${article.number}\u0000${article.revision}`));
        byId('process-use-dialog').showModal();
    });
    byId('copy-process-plan').addEventListener('click', () => {
        const sources = new Map(view.processPlan.uses.filter(use => use.targetErpNumber)
            .map(use => [`${use.targetErpNumber}\u0000${use.targetRevision}`, `${use.targetErpNumber}${use.targetRevision ? ' / Rev. ' + use.targetRevision : ''}`]));
        const sourceSelect = byId('copy-process-source'); sourceSelect.replaceChildren();
        for (const [value, label] of sources) sourceSelect.append(new Option(label, value));
        const targetSelect = byId('copy-process-target');
        const fillTargets = () => {
            const sourceKey = sourceSelect.value;
            targetSelect.replaceChildren();
            for (const article of view.articles) {
                const key = `${article.number}\u0000${article.revision}`;
                if (key !== sourceKey) targetSelect.append(new Option(`${article.number}${article.revision ? ' / Rev. ' + article.revision : ''} · ${article.description}`, key));
            }
        };
        sourceSelect.onchange = fillTargets;
        fillTargets();
        byId('copy-process-plan-dialog').showModal();
    });
    byId('copy-process-plan-form').addEventListener('submit', async event => {
        event.preventDefault();
        const [sourceErpNumber = '', sourceRevision = ''] = byId('copy-process-source').value.split('\u0000');
        const [targetErpNumber = '', targetRevision = ''] = byId('copy-process-target').value.split('\u0000');
        const alreadyAssigned = view.processPlan.uses.some(use => use.targetErpNumber === targetErpNumber && use.targetRevision === targetRevision);
        if (!confirm(alreadyAssigned ? 'Bestehende Tätigkeiten der Zielbaugruppe vollständig durch die Tätigkeiten der Quelle ersetzen?' : 'Tätigkeiten auf die Zielbaugruppe kopieren?')) return;
        await execute(() => request(`${detail.dataset.base}/process-uses/copy`, 'POST', {
            version: view.project.version, sourceErpNumber, sourceRevision, targetErpNumber, targetRevision
        }), 'Tätigkeiten kopiert. Die Zielverwendung kann anschließend lokal angepasst werden.');
        byId('copy-process-plan-dialog').close();
    });
    byId('process-use-form').addEventListener('submit', async event => {
        event.preventDefault();
        const [targetErpNumber = '', targetRevision = ''] = byId('use-target').value.split('\u0000');
        await execute(() => request(`${detail.dataset.base}/process-uses`, 'POST', {
            version: view.project.version, stepGroupId: byId('use-group').value,
            templateId: byId('use-template').value, targetErpNumber, targetRevision
        }), 'Prozessverwendung angelegt.');
        byId('process-use-dialog').close();
    });
    byId('add-operation').addEventListener('click', () => {
        const list = byId('process-operations');
        list.append(makeOperationCard({ sequence: (list.querySelectorAll('.planning-operation-card').length + 1) * 10 }));
    });
    byId('process-editor-form').addEventListener('submit', async event => {
        event.preventDefault();
        const scope = byId('process-editor-form').querySelector('[name="change-scope"]:checked').value;
        const affectedUseIds = [...byId('process-affected-uses').querySelectorAll('[data-affected-use]:checked')].map(input => input.value);
        const availableUseCount = byId('process-affected-uses').querySelectorAll('[data-affected-use]').length;
        if (scope === 'LOCAL' && affectedUseIds.length !== 1) { message('Eine lokale Aenderung muss genau eine Verwendung betreffen.', true); return; }
        if (availableUseCount > 0 && !affectedUseIds.length) { message('Waehle mindestens eine betroffene Verwendungsstelle.', true); return; }
        const operations = [...byId('process-operations').querySelectorAll('.planning-operation-card')].map(card => {
            const value = field => card.querySelector(`[data-field="${field}"]`)?.value ?? '';
            const numberOrNull = field => value(field) === '' ? null : Number(value(field));
            const resources = [...card.querySelectorAll('.planning-resource-row')].map(resource => {
                const resourceValue = field => resource.querySelector(`[data-field="${field}"]`)?.value ?? '';
                return { id: resource.dataset.resourceId, type: resourceValue('type'), articleNumber: resourceValue('articleNumber'),
                    articleRevision: resourceValue('articleRevision'), specialNumber: resourceValue('specialNumber'), description: resourceValue('description'),
                    quantity: resourceValue('quantity') === '' ? null : Number(resourceValue('quantity')), unit: resourceValue('unit'),
                    containerType: resourceValue('containerType'), unitsPerContainer: resourceValue('unitsPerContainer') === '' ? null : Number(resourceValue('unitsPerContainer')),
                    packagingUnit: resourceValue('packagingUnit'), containerAssignment: resourceValue('containerAssignment') };
            });
            const documents = [...card.querySelectorAll('.planning-document-row')].map(item => {
                const documentValue = field => item.querySelector(`[data-field="${field}"]`)?.value ?? '';
                return { id: item.dataset.documentId, type: documentValue('type'), title: documentValue('title'), uri: documentValue('uri'),
                    note: documentValue('note'), external: documentValue('external') === 'true' };
            });
            return { id: card.dataset.operationId, name: value('name'), description: value('description'), sequence: Number(value('sequence')),
                executionsPerUnit: Number(value('executionsPerUnit')), estimatedSeconds: numberOrNull('estimatedSeconds'),
                timeStudySeconds: numberOrNull('timeStudySeconds'), timeStudySource: value('timeStudySource'), timeStudyReference: value('timeStudyReference'),
                actualSeconds: numberOrNull('actualSeconds'), workplaceId: value('workplaceId'), resources, documents };
        });
        await execute(() => request(`${detail.dataset.base}/process-templates/${editingUse.templateId}`, 'PUT', {
            version: view.project.version, expectedRevision: editingUse.templateRevision, changeScope: scope,
            name: byId('process-template-name').value, operations, affectedUseIds
        }), scope === 'LOCAL' ? 'Lokale Prozesskopie gespeichert.' : 'Neue Vorlagenrevision erstellt.');
        byId('process-editor-dialog').close(); editingUse = null;
    });
    document.querySelectorAll('[data-dialog-cancel]').forEach(button => button.addEventListener('click', () => button.closest('dialog').close()));
    document.querySelectorAll('dialog').forEach(dialog => dialog.addEventListener('click', event => {
        if (event.target === dialog) dialog.close();
    }));
    byId('copy-project')?.addEventListener('click', async () => {
        const name = prompt('Name der Projektkopie', `${view.project.name} (Kopie)`);
        if (!name) return;
        await execute(() => request(`${detail.dataset.base}/copy`, 'POST', { name }), 'Planung kopiert.');
        if (view) location.href = `${detail.dataset.base.replace(/\/[^/]+$/, '')}/${view.project.id}`;
    });
    window.addEventListener('beforeunload', event => { if (dirty) { event.preventDefault(); event.returnValue = ''; } });
    request(`${detail.dataset.base}/data`).then(adopt).catch(error => message(error.message, true));
})();