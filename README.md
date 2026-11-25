# CrewBoard – Event Crew Chat

A desktop chat app for event crews. Everyone connects to a shared server, talks in one room, hands out tasks on a shared task board, and runs a quick **readiness check** to confirm the whole crew is ready before the event starts.

![CrewBoard – crew chat in action](docs/screenshots/05-crew-chat.png)

## Features

- **Crew chat** – one shared room with timestamped messages and join/leave notices.
- **Online crew** – live list of who is connected right now.
- **Task board** – shared table of tasks (`#`, `Task`, `Assigned to`, `Status`) with **Add task**, **Accept** and **Mark done** buttons.
- **Readiness check** – start a check and watch crew members move from **Pending** to **Ready** as they press **I'm ready**.
- **Chat commands** – type `/help` in the chat box to list the available commands.
- **Connection panel** – enter the server IP, port and your nickname, then **Connect** / **Disconnect**.

## Screenshots

### 1. Start screen
Before connecting, the chat, task board and readiness buttons are disabled and the status bar reads **Not connected**.

![Start screen, not connected](docs/screenshots/01-disconnected.png)

### 2. Enter your details
Fill in the server IP, port and a nickname, then press **Connect**.

![Connection form filled in](docs/screenshots/02-connect-form.png)

### 3. Connected
Once connected the connection fields lock, the button becomes **Disconnect**, and the chat, task board and readiness controls unlock.

![Connected as Dane](docs/screenshots/03-connected.png)

### 4. More crew join
Every new member appears in **Online crew** and in the **Pending** list of the readiness check, and a join notice is posted to the chat.

![Two crew members online](docs/screenshots/04-crew-online.png)

### 5. Chatting
Messages are timestamped, and the online list updates automatically when someone leaves the room.

![Crew chat with messages](docs/screenshots/05-crew-chat.png)

## Usage

1. Start the CrewBoard server.
2. Launch the CrewBoard client.
3. Enter the **Server IP**, **Port** and your **Nickname**, then click **Connect**.
4. Chat with the crew, add and accept tasks on the **Task board**, and use **Start check** / **I'm ready** to confirm readiness.

<!-- TODO: add build and run commands for the server and client -->

## Project structure

```
.
├── README.md
└── docs/
    └── screenshots/      # images used in this README
```

<!-- TODO: add source folders once they are listed here -->
