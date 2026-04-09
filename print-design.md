  
**🖨️  PRINTDROP**

*Print Anything. Delivered to Your Door.*

| Product Requirements Document Upload. Configure. Receive — Print delivered home in hours. |
| ----- |

| Document Status | Draft — v1.0 |
| :---- | :---- |
| **Product Name** | PrintDrop |
| **Author** | Product Team |
| **Date** | March 2026 |
| **Audience** | Engineering, Design, Operations, Leadership |
| **Inspired By** | Blinkit Print (India) — Quick Commerce Print Delivery |

## **1\. Executive Summary**

PrintDrop is a print-on-demand delivery feature integrated into our e-commerce platform. Users upload documents in PDF, Word (.doc/.docx), or image formats, configure print preferences, pay online, and receive professionally printed copies at their doorstep — within hours.

This mirrors the model pioneered by Blinkit in India, where users can order prints from within the Blinkit app and receive them in under 30 minutes. PrintDrop extends this concept for our platform with a broader file format support, richer print customization, and a scalable fulfillment network.

|  |
| :---- |
|  |
|  |
|  |
|  |
|  |

## **2\. Problem Statement**

Printing remains an offline, friction-heavy task. Users must:

* Travel to a print shop or cyber cafe

* Carry files on a USB drive or send via WhatsApp/email to an unknown operator

* Wait with no visibility into print status or quality

* Handle cash transactions at the counter

This process is particularly painful for:

* Students printing assignments, admit cards, and question papers at odd hours

* Professionals printing contracts, reports, and presentations urgently

* Government document users printing forms, certificates, and affidavits

* Small business owners printing invoices, brochures, and flyers

## **3\. Goals & Success Metrics**

### **3.1 Business Goals**

* Launch a new revenue stream via print orders with 100% gross margins

* Increase order frequency and basket size on the platform

* Establish Print as the default digital print solution and make it as tool to onboard new users.

* Create a defensible moat through fulfillment speed and print quality

### **3.2 User Goals**

* Upload files from any device without visiting a shop

* Get clear visibility on cost, paper, and delivery time before placing the order

* Receive prints that match the quality of professional print shops

* Complete the full transaction in under 3 minutes

## **4\. Target Users & Personas**

| Persona | Description | Key Need | Urgency |
| :---- | :---- | :---- | :---- |
| The Student | 18–25, uses phone-first, prints admit cards, hall tickets, assignments | Fast turnaround, cheap pricing | High — time-critical |
| The Professional | 25–40, prints contracts, NDAs, presentations for meetings | Quality output, confidentiality | Medium — same day |
| The Govt. Document User | All ages, prints application forms, certificates, affidavits | Correct format, exact size | Medium — scheduled |
| The Small Business Owner | Prints invoices, brochures, packaging inserts in bulk | Bulk pricing, consistent quality | Low — recurring |
| The Home User | Prints photos, school projects, boarding passes, recipes | Simple experience, color print | Low — convenience |

## **5\. Scope — In Scope / Out of Scope**

### **5.1 In Scope — MVP**

* File upload: PDF, DOCX, DOC, JPG, PNG, HEIC

* Print configuration: B\&W / Color, Single/Double-sided, Paper size (A4, Letter, Legal), copies count

* Binding options: Don’t give

* Delivery to home address within serviceable zones

* Online payment(only, no cash on delivery):  Airtel Money, MTN Money

* Order tracking from confirmation to delivery

* Basic document preview before confirming order

* Automated pricing engine based on pages, color mode, and paper

### **5.2 Out of Scope — Post MVP**

* Large-format / poster printing

* Custom stationery, visiting cards, or merchandise

* Bulk B2B printing contracts

* In-store pickup option

* Subscription print plans

* Document editing / annotation before printing

## **6\. User Journey & Flow**

### **6.1 End-to-End User Flow**

| Step-by-Step Flow |
| :---- |
| Step 1 — Entry Point: User taps 'Print' from home screen, quick action bar, or search. |
| Step 2 — File Upload: Upload via phone gallery, files app, Google Drive, WhatsApp, or camera scan. |
| Step 3 — Document Preview: System renders a preview; user verifies pages and content. |
| Step 4 — Print Configuration: Select B\&W or Color, copies, page range, paper size, binding. |
| Step 5 — Pricing Summary: Auto-calculated cost shown (per page \+ delivery fee \+ VAT). |
| Step 6 — Delivery Address: Use saved address or add new; show estimated delivery window. |
| Step 7 — Payment: Choose Airtel Money and MTN Money |
| Step 8 — Fulfillment: Order routed to dark store, where we will print it. |
| Step 9 — Delivery: Rider delivers. User receives OTP-verified handoff. |
| Step 10 — Post-Delivery: Rating prompt; reorder option surfaced. |
|  |

## **7\. Feature Specifications**

### **7.1 File Upload Module**

| Feature | Description | Priority |
| :---- | :---- | :---- |
| Supported Formats | PDF, DOCX, DOC, JPG, PNG, | P0 |
| Max File Size | 50 MB per file; up to 5 files per order | P0 |
| Upload Sources | Device storage | P0 |
| Document Preview | Page-by-page render before order confirmation | P1 |
| File Encryption | Files encrypted in transit and at rest; auto-deleted after 24h post-print | P1 |
| Multi-file Order | Combine multiple files into one print order | P1 |
| Page Range Selection | Print specific pages from a document | P1 |

### **7.2 Print Configuration**

| Option | Choices | Default |
| :---- | :---- | :---- |
| Color Mode | Black & White / Full Color | B\&W |
| Sides | Single-sided / Double-sided | Single-sided |
| Paper Size | A4 | A4 |
| Paper Quality | Standard 75 GSM  | Standard 75 GSM |
| Number of Copies | 1 to 50 (slider \+ manual entry) | 1 |
| Binding | Don’t give any option | No Binding |
| Orientation | Auto-detect / Portrait / Landscape | Auto-detect |

### **7.3 Pricing Engine**

* Base price per page: B\&W A4 \= k2.5  | Color A4 \= k5

* Double-sided applies 1.8x page count pricing (not 2x)

* Delivery fee: Calculate as per distance.

* VAT: Need to check.

### **7.5 Order Management**

* Order statuses: Placed → Assigned → Printing → Out for Delivery → Delivered

* Order history: all past print orders with reorder option

* Cancellation: Don’t allow

* Refund policy: replacement for damaged prints

## **8\. Privacy & Security**

| Security Requirements |
| :---- |
| • All uploaded documents encrypted with AES-256 in transit and at rest. |
| • Documents auto-deleted from servers within 24 hours of successful delivery. |
| • Print partners do not retain digital copies post-printing. |
| • No document content is used for ML training or analytics. |
| • Users can manually delete files from order history at any time. |
|  |
|  |

* 

