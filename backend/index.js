const admin = require('firebase-admin');
const express = require('express');

// 1. Initialize Firebase Admin
const serviceAccount = require('./serviceAccountKey.json');

admin.initializeApp({
  credential: admin.credential.cert(serviceAccount)
});

const db = admin.firestore();
const fcm = admin.messaging();

console.log('Backend starting... v1.6 - Blocking Support');

// 2. Listen for NEW messages
const query = db.collectionGroup('messages')
  .orderBy('timestamp', 'desc')
  .limit(10);

const processedMessages = new Set();

query.onSnapshot(snapshot => {
  snapshot.docChanges().forEach(async change => {
    const messageId = change.doc.id;
    const messagePath = change.doc.ref.path;

    if (change.type === 'added' && !processedMessages.has(messageId)) {
      processedMessages.add(messageId);

      const message = change.doc.data();
      const { senderId, receiverId, messageText, timestamp, status, ephemeralPublicKey } = message;

      if (timestamp < (Date.now() - 60000)) return;
      if (status === 'SEEN') return;

      const chatId = change.doc.ref.parent.parent.id;

      try {
        // --- VERIFICATION DELAY ---
        await new Promise(resolve => setTimeout(resolve, 2000));

        const updatedMsg = await db.doc(messagePath).get();
        if (!updatedMsg.exists || updatedMsg.data().status === 'SEEN') {
            return;
        }

        // --- BLOCK CHECK ---
        // Fetch receiver's info to see if they have blocked the sender
        const userDoc = await db.collection('users').doc(receiverId).get();
        if (!userDoc.exists) return;

        const userData = userDoc.data();
        const blockedUids = userData.blockedUids || [];

        if (blockedUids.includes(senderId)) {
            console.log(`[${messageId}] SKIP: Sender ${senderId} is blocked by receiver ${receiverId}.`);
            return;
        }

        const tokens = userData.fcmTokens || [];
        if (tokens.length === 0) return;

        // Fetch sender's name
        const senderDoc = await db.collection('users').doc(senderId).get();
        const senderName = senderDoc.exists ? senderDoc.data().username : 'Someone';

        const payload = {
          data: {
            title: senderName,
            body: messageText || "", // Encrypted text
            senderId: senderId,
            chatId: chatId,
            ephemeralPublicKey: ephemeralPublicKey || ""
          }
        };

        const fcmMessages = tokens.map(token => ({ ...payload, token: token }));
        const response = await fcm.sendEach(fcmMessages);
        console.log(`[${messageId}] SENT [${senderName} -> ${receiverId}]: ${response.successCount} success.`);

        if (response.failureCount > 0) {
           const invalidTokens = [];
           response.responses.forEach((resp, idx) => {
             if (!resp.success && (resp.error.code === 'messaging/registration-token-not-registered' ||
                                   resp.error.code === 'messaging/invalid-registration-token')) {
                 invalidTokens.push(tokens[idx]);
             }
           });
           if (invalidTokens.length > 0) {
             await db.collection('users').doc(receiverId).update({
               fcmTokens: admin.firestore.FieldValue.arrayRemove(...invalidTokens)
             });
           }
        }
      } catch (error) {
        console.error(`[${messageId}] ERROR:`, error);
      }
    }
  });
}, error => {
  console.error('Firestore listener error:', error);
});

const app = express();
app.get('/', (req, res) => res.send('ChatApk Backend v1.6 - Blocking Support Active'));
app.listen(process.env.PORT || 3000, '0.0.0.0');
