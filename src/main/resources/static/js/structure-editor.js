// Structure Editor functionality

class StructureEditor {
    constructor(containerId) {
        this.container = document.getElementById(containerId);
        this.objectId = null;
        this.revisionId = null;
    }

    loadStructure(objectId, revisionId) {
        this.objectId = objectId;
        this.revisionId = revisionId;

        fetch(`/api/structures/${objectId}/revisions/${revisionId}`)
            .then(response => response.json())
            .then(data => this.renderTree(data))
            .catch(error => console.error('Failed to load structure:', error));
    }

    renderTree(node, parentElement = null) {
        const element = document.createElement('div');
        element.className = 'tree-node';
        element.innerHTML = `
            <span class="node-icon">${this.getStatusIcon(node.releaseStatus)}</span>
            <span class="node-id">${node.objectId}</span>
            <span class="node-name">${node.name}</span>
            <span class="node-revision">${node.revision}</span>
            ${node.quantity ? `<span class="node-qty">Qty: ${node.quantity}</span>` : ''}
        `;

        if (parentElement) {
            parentElement.appendChild(element);
        } else {
            this.container.appendChild(element);
        }

        if (node.children && node.children.length > 0) {
            const childContainer = document.createElement('div');
            childContainer.className = 'tree-children';
            element.appendChild(childContainer);

            node.children.forEach(child => {
                this.renderTree(child, childContainer);
            });
        }
    }

    getStatusIcon(status) {
        switch(status) {
            case 'WORKING': return '🚩';
            case 'RELEASED': return '🏁';
            case 'OBSOLETE': return '🚩';
            default: return '';
        }
    }

    addChild(parentId, childId) {
        const data = {
            parentId: parentId,
            parentRevisionId: this.revisionId,
            childId: childId,
            childRevisionId: null,
            quantity: 1
        };

        fetch('/structure/add', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            },
            body: JSON.stringify(data)
        })
        .then(response => response.json())
        .then(() => {
            this.loadStructure(this.objectId, this.revisionId);
        })
        .catch(error => console.error('Failed to add to structure:', error));
    }
}