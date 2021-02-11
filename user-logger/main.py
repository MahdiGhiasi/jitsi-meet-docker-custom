from flask import Flask, request
from waitress import serve
from datetime import datetime

HTTP_HOST = "0.0.0.0"
HTTP_PORT = 8199

app = Flask(__name__)

system_users = ['focus']
# system_rooms = ['jibribrewery', 'jvbbrewery']

def log(room, log):
    room_name = room
    if '@' in room_name:
        room_name = room_name.split('@')[0]
    with open("logs/" + room_name + ".log", "a") as myfile:
        dt = datetime.now().strftime("%d/%m/%Y %H:%M:%S")
        myfile.write(dt + ": " + log + "\n")

@app.route('/log/nicknameUpdate', methods=['POST'])
def nickname_update():
    room = request.form.get('room', '')
    user = request.form.get('user', '')
    nickname = request.form.get('nickname', '')

    log(room, "User '" + user + "' nickname updated to '" + nickname + "'")
    #print("User", user, "on room", room, "nickname updated to", nickname)

    return 'ok'

@app.route('/log/roomCreated', methods=['POST'])
def room_created():
    room = request.form.get('room', '')

    # for system_room in system_rooms:
    #     if room == system_room or room.startswith(system_room + '@'):
    #         return ''

    #print("Room created:", room)
    log(room, "Room created")

    return 'ok'

@app.route('/log/roomDestroyed', methods=['POST'])
def room_destroyed():
    room = request.form.get('room', '')

    # for system_room in system_rooms:
    #     if room == system_room or room.startswith(system_room + '@'):
    #         return ''

    #print("Room destroyed:", room)
    log(room, "Room destroyed")

    return 'ok'

@app.route('/log/userJoined', methods=['POST'])
def user_joined():
    room = request.form.get('room', '')
    user = request.form.get('user', '')

    for system_user in system_users:
        if user.startswith(system_user + '@'):
            return ''

    #print("User", user, "joined to room", room)
    log(room, "User '" + user + "' joined the room")

    return 'ok'

@app.route('/log/userLeft', methods=['POST'])
def user_left():
    room = request.form.get('room', '')
    user = request.form.get('user', '')

    for system_user in system_users:
        if user.startswith(system_user + '@'):
            return ''

    #print("User", user, "left from room", room)
    log(room, "User '" + user + "' has left the room")

    return 'ok'

if __name__ == '__main__':
    serve(app, host=HTTP_HOST, port=HTTP_PORT, threads=10)
