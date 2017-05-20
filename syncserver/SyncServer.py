import os
import sys
import threading
from BaseHTTPServer import BaseHTTPRequestHandler
from BaseHTTPServer import HTTPServer
import json
import socket
import mimetypes
import shutil

class NotifierThread(threading.Thread):
    def __init__(self):
        threading.Thread.__init__(self)
        self.queue = Queue.Queue()

    def run(self):
        sys.stderr.write("Entering notifier threadLoop\n")
        while True :
            item = self.queue.get()
            if (item == 'q') :
                break

            sys.stderr.write("%s\n" % (item))

    def done(self):
        self.enq('q')

    def enq(self, item):
        self.mQueue.put(item)

class SyncServer(HTTPServer):
    class SyncRequestHandler(BaseHTTPRequestHandler):
        def __init__(self, request, client_address, server):
            BaseHTTPRequestHandler.__init__(self, request, client_address, server)
            self.server = server

        def do_GET(self):
            self.log_message("do GET called with path %s" % (repr(self.path)))
            #application/octet-stream
            #XXX
            #path must start with items
            #path can have atmost one more entry.
            #this is the unique id for the item
            #lookup this entry (maybe a hash of the file name) and return if present
            cmds = self.path.strip('/ \t').split('/')
            if len(cmds) == 0 or len(cmds) > 2:
                self.send_error(400)
                return

            if cmds[0] != 'items':
                self.send_error(400)
                return

            with self.server.lock:
                if len(cmds) == 1:
                    self.send_response(200)
                    self.send_header("Content-Type", "application/json")
                    #self.send_header("Content-Length", len(str))
                    self.end_headers()
                    json.dump(self.server.items, self.wfile, indent=0)
                else:
                    i = int(cmds[1])
                    if i in self.server.items :
                        self.send_response(200)
                        self.send_header("Content-Type", "application/octet")
                        self.end_headers()
                        f = open(self.server.items[i], 'rb')
                        shutil.copyfileobj(f, self.wfile)
                    else:
                        self.send_error(404)
                        return

    def __init__(self, addr, port):
        HTTPServer.__init__(self, (addr, port), SyncServer.SyncRequestHandler)
        self.lock = threading.Lock()
        self.items = dict()
        self.uniq_id = 0
        self.add_item('scan/hello-world.txt')
        self.add_item('scan/bad-file.txt')
        self.add_item('scan/STR-S06E10-the-future-of-humanity-with-elon-musk.mp3')


    def add_item(self, path):
        with self.lock:
            self.items[self.uniq_id] = path #q, how does one limit changes to a file while its being fetched? some fcntl?
            self.uniq_id = self.uniq_id + 1

if __name__ == "__main__":
    sys.stderr.write(socket.gethostname() + '\n')
    s = SyncServer(socket.gethostname(), 8080)
    s.serve_forever()
