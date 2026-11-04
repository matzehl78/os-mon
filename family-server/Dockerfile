FROM python:3.12-slim
RUN useradd --system --uid 10001 family && mkdir /data && chown family /data
WORKDIR /app
COPY server.py /app/server.py
USER family
ENV OSMON_FAMILY_HOST=0.0.0.0 OSMON_FAMILY_DB=/data/family.sqlite3 PORT=8787
VOLUME /data
EXPOSE 8787
CMD ["python", "server.py"]
