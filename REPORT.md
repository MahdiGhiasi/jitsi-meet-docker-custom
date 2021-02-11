


The main goals of this project was to:
1. Run an instance of Jitsi Meet on a server running Ubuntu 18.04
2. Modify Jitsi Meet to use OpenFire XMPP Server instead of Prosody.
3. Create a way to log user activity (user join, user left, and nickname changes) in a file.

## Running an instance of Jitsi Meet

To begin, I've started with *[docker-jitsi-meet](https://github.com/jitsi/docker-jitsi-meet)* project, which offers a simple docker-ready instance of Jitsi Meet.

Running the project was relatively painless, being a docker-compose file containing all the necessary components for a Jitsi Meet instance. The only problem was, at the time of writing this, the latest version of docker-jitsi-meet [is broken, so I used a previous version instead](https://community.jitsi.org/t/you-have-been-disconnected-on-fresh-docker-installation/89121/5).

---

## Replacing Prosody with OpenFire

After that, I proceeded to replacing Prosody with OpenFire. To do so, I simply replaced the prosody component in docker-compose with an OpenFire image.

Getting to know XMPP Server concepts and how Jitsi Meet uses them took a while, but as our Jitsi environment is controlled and most settings are available in `.env` file, I could've experiment with the changes in OpenFire quickly, monitoring jvb and jicofo logs each time.

Also, small changes needed to be made in nginx configuration for routing `/http-bind` to OpenFire BOSH server, so the `web` component also has a custom `Dockerfile`.

#### A note about OpenFire configuration

Unfortunately, OpenFire does not offer a good cli configuration solution itself, and the initial setup using their `<autosetup>` is also limited and not working properly in my experience. This means that we cannot have an automated setup of OpenFire, without using OpenFire API or third-party CLIs that are created around it.

For this project, I took another approach instead: Configuring OpenFire via the web-based GUI, and then preserving the configuration files. This method works around the autosetup/cli limitations, and while it's not as neat and readable as configuring OpenFire on a new instance, it's good enough for this purpose. 

But there are maintainability concerns with the method I used, as the configuration process and exact fields that need to be updated is not visible (not to mention the need to change user passwords manually when using this repository), so in a production environment, a cleaner way of configuring OpenFire via API or third-party CLIs is advised.


----

After configuring OpenFire properly, I've created a `Dockerfile` which preserves the configured state of OpenFire, so it can be used on new deployments.

----

## Logging user activity in rooms

First, I investigated the XMPP messages using Gajim XMPP client as well as XMPP packet contents send via BOSH (`/http-bind`) in the browser (Inspect Element -> Network tab) to get an idea of what XMPP messages generally look like and whether I should look inside XMPP packets for this task. This confirmed that I indeed need to look further on intercepting and processing XMPP packets.

The simplest approach would be using Message Audit Policy of OpenFire which logs every packet to a file, and then simply parsing the message logs from a file (perhaps even simply piping the `tail -f` output to a process script). However, while quick to implement, it involves a lot of unnecessary IO, and generally is not a clean solution in my opinion.

I also briefly explored XMPP Components, as they are platform agnostic and work on any XMPP Server, but the documentation around those are scarce and I couldn't make sure whether those APIs give me enough to do what I need to do, and there are only a few libraries implementing XMPP Components API as well, which was another challenge.

Then, I switched over to creating a plugin for OpenFire for user activity logging.

The documentation and resources for OpenFire plugin development is also scarce, so I decided to get familiar with OpenFire plugin development by starting from an existing plugin. After browsing available extensions, I decided to use [OpenFire ContentFilter plugin](https://github.com/igniterealtime/openfire-contentFilter-plugin), as it's a relatively small and lightweight plugin (meaning probably not much extra stuff that I don't need to learn for this task), and also what it does is close to what I'm going to do. This plugin intercepts all packets to find out and filter disallowed messages, so it's probably a good pointer for starting packet intercepting.

At last, the plugin is using `MUCEventListener` interface for getting notified of room creation and destroy events as well as user join/leave events. But the nickname used in Jitsi is not the same as user id in OpenFire and is a concept in Jitsi Meet itself. So we need to process certain packet contents to find out about Nickname updates.

To process packets, the plugin also implements `PacketInterceptor` so every packet can go through the plugin. Then, the plugin looks for `<nick>` tag inside `<presense>` root node in packets sent from a user to the room group address. The `<nick>` tag can appear multiple times in my experience, so a simple mechanism to avoid duplicate logs needed to be implemented as well.

Ultimately, the plugin sends logs via asynchronous http requests to a simple external log saving service written in Python/Flask. We could alternatively used rsyslog or something like that in case we wanted text logging, or we can modify the Python script (which currently just saves to a file) to store data in a database in case we want real time access or certain processing to the data.
