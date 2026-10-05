ANOOP'S FINANCE TRACKER - STANDALONE WEBSITE
=============================================

This version runs on its own. It doesn't need Claude or an internet connection.
Your data is saved in the browser on the device you use.


OPTION 1 - OPEN THE FILE DIRECTLY (quickest)
--------------------------------------------
Open index.html with Chrome, Edge, Safari or Firefox.
- Computer: double-click index.html.
- iPhone/Android: save index.html to Files, then open it in the browser.

Everything works, including charts, PDF reports, logins, the library and the calendar.


OPTION 2 - PUT IT ONLINE AS A WEBSITE / PHONE APP (recommended)
---------------------------------------------------------------
Upload all files in this folder to any free static host:
- Netlify Drop: go to https://app.netlify.com/drop and drag the whole folder in. You get a web address straight away.
- GitHub Pages, Cloudflare Pages or Vercel work the same way.

Then open the address on your phone and choose:
- iPhone (Safari): Share > Add to Home Screen
- Android (Chrome): menu > Install app / Add to Home screen

It opens like a normal app with its own icon and keeps working offline.


MOVE YOUR DATA FROM THE CLAUDE VERSION
--------------------------------------
1. In the Claude version, sign in, then go to Settings > Download backup.
2. In this version, go to Settings > Restore from backup and choose that file.
   You can create a login here first, so the restored data is encrypted with your password.


GOOD TO KNOW
------------
- Data stays only on that device and browser. A different phone or computer starts empty,
  so use Download backup / Restore from backup to move data between them.
- Clearing the browser's site data deletes the tracker's data. Download a backup regularly.
- If you forget a login password, that account's data can't be recovered. There is no email reset.
- AI assistant: this version has no Claude. To use the chat button, go to Settings > AI assistant and enter your Flowise chatflow ID and host address (a public https address of your Flowise server). Without that, the chat button just explains how to set it up.
- Things that need Claude's cloud and are NOT in this version:
  - automatic economic calendar updates (type actual figures yourself in each event)
  - email updates and scheduled reminders
  - syncing between devices
  - Google Drive backup
- Reminders and alerts pop up while the page is open.
- Market prices are entered by you. The tracker doesn't connect to brokers.
- Reading uploaded PDF and Word files in the Learning library needs an internet connection
  the first time. After that, the hosted version keeps the readers for offline use.
