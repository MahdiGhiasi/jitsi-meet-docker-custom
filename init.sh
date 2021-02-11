mkdir -p ~/.jitsi-meet-cfg/{web,web/letsencrypt,transcripts,jicofo,jvb,jigasi,jibri}
mkdir ~/jitsi-logs
docker-compose build
docker-compose up -d
