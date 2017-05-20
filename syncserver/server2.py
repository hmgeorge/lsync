import os
import sys
import threading
import socket
import asyncore
import Queue
import time
from asynchat import async_chat
from fsevents import Observer, Stream
from fsevents import IN_CREATE

#TypeError: Error when calling the metaclass bases
#module.__init__() takes at most 2 arguments (3 given)
#stackoverflow/typeerror-module-init-takes-at-most-2-arguments-3-given
class ClientHandler2(async_chat):
    def __init__(self, sock, server):
        async_chat.__init__(self, sock)
        self.server = server
        self.first_read = False
        self.out_buffer = "hello you are using async chat"
        self.set_terminator('\n')
        self.in_buffer = []
        self.started = False

    def readable(self):
        return self.started and async_chat.readable(self)

    def writable(self):
        if not self.started:
            return False

        if self.first_read:
            return async_chat.writable(self)
        return False

    def collect_incoming_data(self, data):
        self.log("collect_incoming_data %s" % (repr(data)))
        self.in_buffer.append(data)
    
    def found_terminator(self):
        self.log("found_terminator %s" % (repr(self.in_buffer)))
        self.in_buffer = []
        self.first_read = True
        async_chat.push(self, self.out_buffer)

    def handle_close(self):
        self.log("Client closed the session")
        self.server.remove(self)
        self.close()

    def start(self):
        self.started = True

    def id(self):
        return self._fileno

class ClientHandler(asyncore.dispatcher):
    def __init__(self, sock, server):
        asyncore.dispatcher.__init__(self, sock)
        self.first_read = False
        self.out_buffer = "hello you are now connected"
        self.server = server

    def readable(self):
        return True

    def writable(self):
        return self.first_read and len(self.out_buffer)

    def handle_read(self):
        data = self.recv(512)
        if len(data) > 0 :
            self.first_read = True
            self.log("handle_read -> %s" % (repr(data)))

    def handle_write(self):
        self.log("handle write %s" % (repr(self.out_buffer)))
        sent = asyncore.dispatcher.send(self, self.out_buffer[:4])
        self.out_buffer = self.out_buffer[sent:]

    def handle_close(self):
        self.server.remove(self)
        self.close()

    def start(self):
        self.add_channel()  #add self to the module socket map

    def id(self):
        return self._fileno

class Server(asyncore.dispatcher):
    def __init__(self, addr, port):
        asyncore.dispatcher.__init__(self)

        #create socket adds socket to the module socket map
        self.create_socket(socket.AF_INET, socket.SOCK_STREAM)
        self.set_reuse_addr()
        self.bind((addr, port))
        self.listen(5)
        self.client_handlers = dict()
        self.lock = threading.Lock()

    def handle_accept(self):
        self.log('handle accept')
        (client_sock, addr) = self.accept()
        self.log('accept conn from %s' % (str(addr)))

        ch = ClientHandler2(client_sock, self)
        ch.start()
        with self.lock:
            self.client_handlers[ch.id()] = ch
        # accepting sockets are never connected, they "spawn" new
        # sockets that are connected
        # therefore no need to handle read or write events
        #TBD spawn an async_chat session on accepting a connection

    def readable(self):
        return True

    def writable(self):
        return False #server just listens for connections

    def remove(self, client):
        with self.lock:
            self.log("remove client %s" % (repr(client)))
            del self.client_handlers[client.id()]

class NotifierThread(threading.Thread):
    def __init__(self):
        threading.Thread.__init__(self)
        self.queue = Queue.Queue()

    def run(self):
        sys.stderr.write("Entering notifier threadLoop\n")
        while True:
            try :
                time.sleep(1)
                self.socket = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
                self.socket.connect((socket.gethostname(), 8080))
            except socket.error as e:
                if e.errno == 61 :
                    sys.stderr.write("connection refused, retry\n")
                else :
                    sys.stderr.write("some other error, abort\n")
                    return False
            finally:
                break

        sys.stderr.write("connected, wait on q\n")
        while True :
            item = self.queue.get()
            if (item == 'q') :
                break

            sys.stderr.write("%s\n" % (item))

    def done(self):
        self.enq('q')

    def enq(self, item):
        self.mQueue.put(item)

def xmain():
    notifier = NotifierThread()
    notifier.start()
    server = Server(socket.gethostname(), 8080)
    asyncore.loop()
    notifier.done()
    notifier.join()

if __name__ == "__main__":
    xmain()
