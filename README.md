# A customized Jitsi Meet
This repository contains a customized version of Jitsi Meet, distributed via a `docker-compose` file.

In this repository, I've replaced Prosody with OpenFire for XMPP communications of Jitsi, and adds a user activity logger service on top as a combination of an OpenFire plugin and a simple REST log saver component written in Flask.

**WARNING:** Sample secrets and passwords are provided in this repo. If you are using this repository in any way, it's strongly advised that you change all the secrets and passwords. To do so, you can run `gen-passwords.sh` to update the `.env` file, then you'll need to reset OpenFire admin password ([described here](https://discourse.igniterealtime.org/t/how-to-reset-admin-password-on-openfire-version-4-3-1/83990)), and finally update the passwords for `focus` and `jvb` users as well as XMPP shared component secret.

---

To run the project, clone the repository in an environment with Docker and Docker-Compose installed, change all secrets (described above), replace *meet.ghiasi.net* with your domain name in `.env` and `openfire/initial-data/openfire/embedded-db/openfire.script` and then run `init.sh`. This script creates necessary folders for configuration and logging, and then runs the containers.

---

By default, the configuration files will be stored in `~/jitsi-meet-cfg` and user activity logs will be stored in separate `.log` files per room in `~/jitsi-logs/userlogger/`.

---

There's also a report about the process of doing what's done in this project. You can read more about the details [here](REPORT.md).
