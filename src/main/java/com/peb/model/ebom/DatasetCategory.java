package com.peb.model.ebom;

/**
 * Dataset category enumeration for eBOM objects
 *
 * Reihenfolge = Sortierreihenfolge im Viewer (ordinal())
 * viewerCompatible         = Anzeige im 3D Viewer erlaubt
 * embeddedViewerCompatible = Anzeige im Embedded Viewer (PDF/Office) erlaubt
 */
public enum DatasetCategory {

    PDF_DOCUMENT    ("PDF Document",      false, true),
    PDF_DRAWING     ("PDF Drawing",       false, true),
    EXCEL_TABLE     ("Excel Table",       false, true),
    CE_DOCUMENT     ("CE Document",       false, false),
    DESIGN_NX       ("Design NX",         true,  false),
    DESIGN_INVENTOR ("Design Inventor",   true,  false),
    DESIGN_SOLIDEDGE("Design SolidEdge",  true,  false),
    DESIGN_RENDERING("Design Rendering",  true,  false),
	DESIGN_JT		("Design Rendering JT",  true,  false),
    THUMBNAIL       ("Thumbnail",         false, false),
    OTHER           ("Other",             false, false);

    private final String  displayName;
    private final boolean viewerCompatible;
    private final boolean embeddedViewerCompatible;

    DatasetCategory(String displayName,
                    boolean viewerCompatible,
                    boolean embeddedViewerCompatible) {
        this.displayName              = displayName;
        this.viewerCompatible         = viewerCompatible;
        this.embeddedViewerCompatible = embeddedViewerCompatible;
    }

    public String getDisplayName() { return displayName; }

    public boolean isViewerCompatible() { return viewerCompatible; }

    public boolean isEmbeddedViewerCompatible() { return embeddedViewerCompatible; }
}