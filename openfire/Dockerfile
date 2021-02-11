FROM gizmotronic/openfire:4.4.4
ADD initial-data /initial-data
COPY entrypoint2.sh /sbin/entrypoint2.sh
RUN chmod 755 /sbin/entrypoint2.sh
ENTRYPOINT ["/sbin/entrypoint2.sh"]
