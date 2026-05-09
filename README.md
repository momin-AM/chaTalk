# ChatApk

Scalable text-only Android chat app scaffolded with Kotlin, Jetpack Compose, MVVM, Firebase Auth, Cloud Firestore, and Firebase Cloud Messaging.

## Project Structure

```text
app/src/main/java/com/example/chatapk
├── data
│   ├── firebase          # Firestore snapshot Flow adapters and mappers
│   └── repository        # Firebase Auth, user, and chat implementations
├── di                    # Lightweight app container and notification channels
├── domain
│   ├── model             # UserProfile, Chat, ChatMessage, MessageStatus
│   └── repository        # Repository contracts
├── notifications         # FCM service
└── presentation
    ├── auth              # Login/signup screen and ViewModel
    ├── chat              # One-to-one conversation screen and ViewModel
    ├── chatlist          # Recent chats and people list
    ├── common            # Shared UI helpers
    └── navigation        # Compose navigation graph
```

## Firebase Setup

1. Create a Firebase project.
2. Add an Android app with package name `com.example.chatapk`.
3. Download `google-services.json`.
4. Put it here:

```text
app/google-services.json
```

5. Ensure Android Studio creates `local.properties`, or copy `local.properties.example` to `local.properties` and set `sdk.dir`.
6. Enable Firebase Authentication with Email/Password.
7. Create a Cloud Firestore database.
8. Enable Firebase Cloud Messaging.
9. Import the composite indexes from `firestore.indexes.json`, or create them from the Firestore console prompts.
10. Sync the Gradle project in Android Studio.

The Google Services plugin is applied only when `app/google-services.json` exists, so the project can sync before Firebase config is added.

## Firestore Schema

```text
users/{uid}
  uid: string
  email: string
  username: string
  profilePictureUrl: string | null
  online: boolean
  lastSeen: number
  fcmTokens: string[]

chats/{chatId}
  participantIds: string[]       # sorted pair of user ids
  participantNames: map          # uid -> username snapshot
  lastMessage: string
  lastMessageSenderId: string
  lastMessageAt: number
  unreadCounts: map              # uid -> number
  typing: map                    # uid -> boolean
  createdAt: number

chats/{chatId}/messages/{messageId}
  senderId: string
  receiverId: string
  messageText: string
  timestamp: number
  status: SENT | DELIVERED | SEEN
  reactions: map                 # userId -> emoji
```

`chatId` is deterministic: the two participant ids sorted and joined with `_`.

## Suggested Firestore Rules Starter

```js
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {
    function signedIn() {
      return request.auth != null;
    }

    function isParticipant(chatId) {
      return signedIn()
        && request.auth.uid in get(/databases/$(database)/documents/chats/$(chatId)).data.participantIds;
    }

    match /users/{uid} {
      allow read: if signedIn();
      allow create, update: if signedIn() && request.auth.uid == uid;
    }

    match /chats/{chatId} {
      allow read, update: if isParticipant(chatId);
      allow create: if signedIn()
        && request.auth.uid in request.resource.data.participantIds
        && request.resource.data.participantIds.size() == 2;

      match /messages/{messageId} {
        allow read: if isParticipant(chatId);
        allow create: if isParticipant(chatId)
          && request.resource.data.senderId == request.auth.uid;
        allow update: if isParticipant(chatId);
      }
    }
  }
}
```

## Notifications

The app stores FCM tokens and can display incoming FCM messages. Sending push notifications for new Firestore messages requires a trusted server environment, typically Firebase Cloud Functions triggered by `chats/{chatId}/messages/{messageId}`. Do not put FCM server keys in the Android app.
