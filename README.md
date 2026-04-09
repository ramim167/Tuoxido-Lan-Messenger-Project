# 💬 Tuoxido — Modern LAN Messenger

> A full-featured desktop messaging app built with JavaFX, MongoDB Atlas, and Firebase Auth.  
> Real-time chat, video/audio calls, moments, friend system — all in one sleek UI.

---

## ✨ Features

| Feature | Description |
|---|---|
| 💬 **Messaging** | Real-time 1-on-1 and group chat with message history |
| 📞 **P2P Audio Calls** | Direct peer-to-peer voice calls over TCP |
| 🎥 **P2P Video Calls** | Live webcam video calls with camera toggle |
| 📸 **Moments** | Post photos and captions — like a social feed for your network |
| 👥 **Friends System** | Send/accept friend requests, view friend profiles |
| 🚫 **Block Users** | Block and unblock users from your privacy settings |
| 🌗 **Light / Dark Theme** | Full theme switching with smooth transitions |
| 🔐 **Firebase Auth** | Secure email/password login with email verification |
| 🖼️ **Profile Pictures** | Upload profile photos via ImgBB CDN |
| 🔔 **Auto Login** | Stay signed in across app restarts |

---

## 🛠️ Tech Stack

- **Java 17** + **JavaFX 17**
- **MongoDB Atlas** — cloud database for messages, users, groups, moments
- **Firebase Authentication** — sign up, login, email verification, password reset
- **ImgBB API** — image hosting for profile pictures and moments
- **Webcam Capture** — webcam access for video calls
- **Ikonli** — Material Design icons
- **Gson** — JSON parsing for Firebase REST API
- **Maven** — build and dependency management

---

## 🚀 Getting Started

### Option 1 — Run the EXE (easiest)
Download the latest release from the [Releases](../../releases) page, double click and run. No setup needed.

### Option 2 — Run from Source

**Prerequisites**
- Java 17 or higher
- Maven 3.8+
- IntelliJ IDEA (recommended)

**Clone and run**
```bash
git clone https://github.com/YOUR_USERNAME/Tuoxido.git
cd Tuoxido
mvn clean javafx:run
```

---

## 📁 Project Structure

```
Tuoxido/
├── src/
│   └── main/
│       ├── java/com/lanmessenger/
│       │   ├── MainApp.java              # App entry, splash screen
│       │   ├── LoginController.java      # Login + auto-login
│       │   ├── MainController.java       # Main chat UI
│       │   ├── MessageService.java       # Chat logic
│       │   ├── MomentsService.java       # Moments/posts
│       │   ├── P2PAudioService.java      # Voice calls
│       │   ├── P2PVideoService.java      # Video calls
│       │   ├── GroupService.java         # Group chats
│       │   ├── UserService.java          # User management
│       │   ├── FirebaseAuthService.java  # Auth via Firebase REST
│       │   ├── MongoDatabaseService.java # MongoDB connection
│       │   └── AppConfig.java            # Config loader
│       └── resources/
│           ├── *.fxml                    # UI layouts
│           ├── *.css                     # Stylesheets
│           └── assets/                   # Images, icons
└── pom.xml
```

---

## 📸 Screenshots

> Coming soon

---

## 👨‍💻 Developer

Built by **Nafis Mohtasim** — 2026

---

## 📄 License

This project is for educational purposes.
