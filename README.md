# chaTalk 💬

**chaTalk** is a modern, secure, and production-ready Android chat application. Built with a focus on privacy and user experience, it features full **End-to-End Encryption (E2EE)**, real-time synchronization, and a familiar WhatsApp-style interface.

## ✨ Key Features

- 🔒 **End-to-End Encryption (E2EE)**: Messages are secured using **ECDH** (Elliptic Curve Diffie-Hellman) for key exchange and **AES-256-GCM** for message encryption. Your private keys never leave your device.
- ⚡ **Real-time Messaging**: Instant message delivery and status updates (Sent ✓, Delivered ✓✓, Seen ✓✓) powered by Firebase Firestore.
- 🔔 **Encrypted Push Notifications**: Receive real-time alerts even when the app is closed. Notification contents are decrypted locally on your device.
- 🌓 **Dynamic Theming**: Full support for Light and Dark modes with a seamless toggle in settings.
- 😊 **Message Interactions**: React to messages with emojis, copy text to clipboard, or delete messages for everyone.
- 🔄 **Auto-Updates**: Integrated GitHub release tracking—the app notifies you when a new version is available and handles the update process.
- ⌨️ **Smart UI**: Interactive keyboard handling with sticky headers and auto-scrolling conversations.

## 🛠 Tech Stack

- **Language**: [Kotlin](https://kotlinlang.org/)
- **UI Framework**: [Jetpack Compose](https://developer.android.com/jetpack/compose) (100% declarative UI)
- **Database**: [Firebase Firestore](https://firebase.google.com/docs/firestore) (Cloud) & [Room](https://developer.android.com/training/data-storage/room) (Local Cache)
- **Authentication**: [Firebase Auth](https://firebase.google.com/docs/auth) (Email/Password)
- **Notifications**: [Firebase Cloud Messaging (FCM)](https://firebase.google.com/docs/cloud-messaging)
- **Architecture**: MVVM (Model-View-ViewModel) with Clean Architecture principles and Repository pattern.
- **Dependency Injection**: Manual DI via App Container.
- **Cryptography**: Java Cryptography Architecture (JCA) with ECDH secp256r1.

## 📂 Project Structure

```text
app/src/main/java/com/example/chatapk
├── data
│   ├── firebase          # Firestore adapters and data mapping
│   ├── local             # Room Database, DAOs, and Entities
│   └── repository        # Implementation of Auth, User, and Chat logic
├── di                    # AppContainer for dependency management
├── domain
│   ├── model             # Business models (Chat, Message, User)
│   └── repository        # Interface definitions for repositories
├── notifications         # Firebase Messaging Service
├── security              # EncryptionManager for E2EE logic
└── presentation          # UI Layers
    ├── auth              # Login & Registration
    ├── chat              # Message thread & context menus
    ├── chatlist          # Recent conversations & User search
    ├── settings          # Profile management & App preferences
    └── navigation        # Compose Navigation Graph
```

## 🚀 Getting Started

### 1. Firebase Configuration
- Create a project in the [Firebase Console](https://console.firebase.google.com/).
- Add an Android App with package name `com.example.chatapk`.
- Download `google-services.json` and place it in the `app/` directory.
- Enable **Email/Password** Authentication.
- Create a **Firestore** Database and a **Storage** bucket (optional).

### 2. Firestore Security Rules
Paste the following rules into your Firebase Console to secure your data:

```javascript
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {
    function isSignedIn() { return request.auth != null; }
    function isParticipant(chatId) {
      let chat = get(/databases/$(database)/documents/chats/$(chatId)).data;
      return isSignedIn() && request.auth.uid in chat.participantIds;
    }
    match /users/{userId} {
      allow read: if isSignedIn();
      allow write: if isSignedIn() && request.auth.uid == userId;
    }
    match /chats/{chatId} {
      allow read, update: if isSignedIn() && request.auth.uid in resource.data.participantIds;
      allow create: if isSignedIn() && request.auth.uid in request.resource.data.participantIds;
      match /messages/{messageId} {
        allow read, create, update, delete: if isParticipant(chatId);
      }
    }
  }
}
```

### 3. Backend (Optional)
The project includes a Node.js backend (`/backend`) designed to run as a Firebase Cloud Function or a standalone server to handle FCM push notifications for new messages.

## 🗺 Roadmap
- [ ] **Google Drive Backup**: Support for backing up private keys and chat history to the user's personal Google Drive.
- [ ] **Media Sharing**: Support for sending images, videos, and voice notes.
- [ ] **Group Chats**: Encrypted multi-user conversations.
- [ ] **Biometric Lock**: Secure access to the app using Fingerprint/FaceID.

---
*We will implement more functions soon!*
