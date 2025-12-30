# AutoAction Web Prototype

This is a standalone web interface to test the AutoAction logic.

## How to Run

### Option 1: Desktop Testing
Simply open `index.html` in Chrome, Edge, or Firefox.

### Option 2: Mobile Testing (Phone)
To test on your phone, you need to serve these files over your local network.

1. Open a terminal in this folder (`web-prototype`).
2. Run a simple HTTP server. 
   - Python: `python -m http.server 8000`
   - Node: `npx serve`
3. Find your computer's local IP address (e.g., `ipconfig` on Windows -> IPv4 Address, usually `192.168.x.x`).
4. On your phone, open: `http://<YOUR_IP>:8000`
5. Upload a screenshot and verify the action.

## Features
- **Client-Side OCR**: uses Tesseract.js (requires internet on first load to download language data).
- **Auto-Classify**: Detects Tracking #, Addresses, Dates, and Prices.
