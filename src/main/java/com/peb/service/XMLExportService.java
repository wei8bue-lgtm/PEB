package com.peb.service;

import com.peb.model.ebom.EBOMObject;
import com.peb.model.structure.StructureNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.StringWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Service for exporting eBOM structures to XML
 * Machine-readable format for external systems
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class XMLExportService {

    private final StructureService structureService;
    private final EBOMService ebomService;

    /**
     * Export complete eBOM structure to XML
     */
    public String exportStructureToXML(String objectId, Long revisionId) throws Exception {
        StructureNode tree = structureService.getStructureTree(objectId, revisionId);
        EBOMObject rootObject = ebomService.getEBOMObject(objectId);

        DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
        DocumentBuilder docBuilder = docFactory.newDocumentBuilder();
        Document doc = docBuilder.newDocument();

        // Root element
        Element rootElement = doc.createElement("eBOM_Export");
        doc.appendChild(rootElement);

        // Add metadata
        addMetadata(doc, rootElement, rootObject);

        // Add structure
        Element structureElement = doc.createElement("Structure");
        rootElement.appendChild(structureElement);

        buildXMLElement(doc, structureElement, tree);

        // Transform to string
        return transformToString(doc);
    }

    /**
     * Export eBOM structure with detailed attributes
     */
    public String exportDetailedStructureToXML(String objectId, Long revisionId) throws Exception {
        StructureNode tree = structureService.getStructureTree(objectId, revisionId);
        EBOMObject rootObject = ebomService.getEBOMObject(objectId);

        DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
        DocumentBuilder docBuilder = docFactory.newDocumentBuilder();
        Document doc = docBuilder.newDocument();

        // Root element with namespace
        Element rootElement = doc.createElement("eBOM_Export");
        rootElement.setAttribute("xmlns:xsi", "http://www.w3.org/2001/XMLSchema-instance");
        rootElement.setAttribute("version", "1.0");
        doc.appendChild(rootElement);

        // Add metadata
        addDetailedMetadata(doc, rootElement, rootObject);

        // Add structure with full details
        Element structureElement = doc.createElement("Structure");
        rootElement.appendChild(structureElement);

        buildDetailedXMLElement(doc, structureElement, tree);

        // Transform to string
        return transformToString(doc);
    }

    private void addMetadata(Document doc, Element rootElement, EBOMObject rootObject) {
        Element metadata = doc.createElement("Metadata");
        rootElement.appendChild(metadata);

        addElement(doc, metadata, "ExportDate", LocalDateTime.now()
            .format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        addElement(doc, metadata, "ExportSystem", "PEB - Production Engineering Backbone");
        addElement(doc, metadata, "RootObjectID", rootObject.getId());
        addElement(doc, metadata, "RootObjectName", rootObject.getName());
        addElement(doc, metadata, "RootObjectType", rootObject.getObjectType().getDisplayName());
    }

    private void addDetailedMetadata(Document doc, Element rootElement, EBOMObject rootObject) {
        Element metadata = doc.createElement("Metadata");
        rootElement.appendChild(metadata);

        addElement(doc, metadata, "ExportDate", LocalDateTime.now()
            .format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        addElement(doc, metadata, "ExportSystem", "PEB - Production Engineering Backbone");
        addElement(doc, metadata, "ExportVersion", "1.0");
        addElement(doc, metadata, "RootObjectID", rootObject.getId());
        addElement(doc, metadata, "RootObjectName", rootObject.getName());
        addElement(doc, metadata, "RootObjectType", rootObject.getObjectType().getDisplayName());
        
        if (rootObject.getCreatedDate() != null) {
            addElement(doc, metadata, "CreatedDate", rootObject.getCreatedDate()
                .format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        }
        if (rootObject.getCreatedBy() != null) {
            addElement(doc, metadata, "CreatedBy", rootObject.getCreatedBy());
        }
    }

    private void buildXMLElement(Document doc, Element parentElement, StructureNode node) {
        Element objectElement = doc.createElement("Component");
        parentElement.appendChild(objectElement);

        // Basic attributes
        objectElement.setAttribute("id", node.getObjectId());
        objectElement.setAttribute("type", node.getObjectType());
        objectElement.setAttribute("revision", node.getRevision() != null ? node.getRevision() : "");

        addElement(doc, objectElement, "Name", node.getName());
        addElement(doc, objectElement, "ReleaseStatus", node.getReleaseStatus());
        
        if (node.getQuantity() != null) {
            addElement(doc, objectElement, "Quantity", node.getQuantity().toString());
        }
        
        if (node.getPositionNumber() != null) {
            addElement(doc, objectElement, "PositionNumber", node.getPositionNumber());
        }

        // Add children recursively
        if (!node.getChildren().isEmpty()) {
            Element children = doc.createElement("Children");
            objectElement.appendChild(children);
            
            for (StructureNode child : node.getChildren()) {
                buildXMLElement(doc, children, child);
            }
        }
    }

    private void buildDetailedXMLElement(Document doc, Element parentElement, StructureNode node) {
        Element objectElement = doc.createElement("Component");
        parentElement.appendChild(objectElement);

        // Basic identification
        Element identification = doc.createElement("Identification");
        objectElement.appendChild(identification);
        addElement(doc, identification, "ID", node.getObjectId());
        addElement(doc, identification, "Name", node.getName());
        addElement(doc, identification, "Type", node.getObjectType());
        addElement(doc, identification, "Revision", node.getRevision() != null ? node.getRevision() : "");

        // Status information
        Element status = doc.createElement("Status");
        objectElement.appendChild(status);
        addElement(doc, status, "ReleaseStatus", node.getReleaseStatus());
        addElement(doc, status, "IsReleased", String.valueOf(node.isReleased()));

        // Get detailed attributes from actual object
        try {
            String prefix = node.getObjectId().substring(0, 2);
            if (!prefix.equals("DS") && !prefix.equals("DR")) {
                EBOMObject ebomObject = ebomService.getEBOMObject(node.getObjectId());
                
                Element attributes = doc.createElement("Attributes");
                objectElement.appendChild(attributes);
                
                if (ebomObject.getDepartment() != null) {
                    addElement(doc, attributes, "Department", ebomObject.getDepartment());
                }
                if (ebomObject.getMass() != null) {
                    addElement(doc, attributes, "Mass", ebomObject.getMass());
                    attributes.getElementsByTagName("Mass").item(0).getAttributes()
                        .setNamedItem(doc.createAttribute("unit"));
                    attributes.getElementsByTagName("Mass").item(0).getAttributes()
                        .getNamedItem("unit").setTextContent("kg");
                }
                if (ebomObject.getMaterial() != null) {
                    addElement(doc, attributes, "Material", ebomObject.getMaterial());
                }
                if (ebomObject.getSupplier() != null) {
                    addElement(doc, attributes, "Supplier", ebomObject.getSupplier());
                }
                if (ebomObject.getLinkedDesignId() != null) {
                    addElement(doc, attributes, "LinkedDesign", ebomObject.getLinkedDesignId());
                }
                
                // Creation info
                Element creationInfo = doc.createElement("CreationInfo");
                objectElement.appendChild(creationInfo);
                if (ebomObject.getCreatedDate() != null) {
                    addElement(doc, creationInfo, "CreatedDate", 
                        ebomObject.getCreatedDate().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
                }
                if (ebomObject.getCreatedBy() != null) {
                    addElement(doc, creationInfo, "CreatedBy", ebomObject.getCreatedBy());
                }
            }
        } catch (Exception e) {
            log.warn("Could not fetch detailed attributes for {}: {}", node.getObjectId(), e.getMessage());
        }

        // Structure information
        if (node.getQuantity() != null || node.getPositionNumber() != null) {
            Element structureInfo = doc.createElement("StructureInfo");
            objectElement.appendChild(structureInfo);
            
            if (node.getQuantity() != null) {
                addElement(doc, structureInfo, "Quantity", node.getQuantity().toString());
            }
            if (node.getPositionNumber() != null) {
                addElement(doc, structureInfo, "PositionNumber", node.getPositionNumber());
            }
        }

        // Add children recursively
        if (!node.getChildren().isEmpty()) {
            Element children = doc.createElement("Children");
            objectElement.appendChild(children);
            
            for (StructureNode child : node.getChildren()) {
                buildDetailedXMLElement(doc, children, child);
            }
        }
    }

    private void addElement(Document doc, Element parent, String tagName, String textContent) {
        Element element = doc.createElement(tagName);
        if (textContent != null && !textContent.isEmpty()) {
            element.setTextContent(textContent);
        }
        parent.appendChild(element);
    }

    private String transformToString(Document doc) throws TransformerException {
        TransformerFactory transformerFactory = TransformerFactory.newInstance();
        Transformer transformer = transformerFactory.newTransformer();
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
        transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");

        StringWriter writer = new StringWriter();
        DOMSource source = new DOMSource(doc);
        StreamResult result = new StreamResult(writer);
        transformer.transform(source, result);

        return writer.toString();
    }
}