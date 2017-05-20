import os
import sys
import threading
import time
import Queue
import tempfile
import socket
import select

'''
Having seen the mechanics behind the iterator protocol, it is easy to add iterator behavior to your classes.
Define an __iter__() method which returns an object with a next() method (raise StopIteration when iteration ends).
If the class defines next(), then __iter__() can just return self:
'''

'''
generators (yield) are an alternate option. create iter and next automatically
'''

'''
Among the acceptable object types in the sequences are Python file objects (e.g.
sys.stdin, or objects returned by open() or os.popen()), socket objects returned by
socket.socket().
You may also define a wrapper class yourself, as long as it has an appropriate fileno()
method (that really returns a file descriptor, not just a random integer).
'''

class WorkerThread(threading.Thread):
    def __init__(self, name):
        threading.Thread.__init__(self)
        self.mName = name
        self.mDone = False
        self.mQueue = Queue.Queue() #

    def run(self):
        sys.stderr.write("Entering threadLoop\n")
        while True : #don't neeed mDone since q is checked
            item = self.mQueue.get()
            if (item == 'q') :
                break

            sys.stderr.write("%s\n" % (item))

    def done(self):
        self.enq('q')

    def enq(self, item):
        self.mQueue.put(item)


class IO :
    def onRead(self):
        sys.stderr.write("read?\n")
        pass

    def onWrite(self):
        pass

    def onError(self, msg):
        pass

    def onExit(self):
        pass

    def fileno():
        raise Exception('fileno must be implemented')

class IOMonitor(threading.Thread):
    def __init__(self):
        threading.Thread.__init__(self)
        self.mName = "monitor";
        self.mReaders = dict() # socket/fd->interface implementing onRead/onWrite/onError
        self.mWriters = dict()
        self.mLock = threading.Lock()
        self.mCondition = threading.Condition(self.mLock)
        self.mReadersToRemove = set()
        self.mWritersToRemove = set()
        self.mDone = False
        r, w = os.pipe()
        self.mQuit = (os.fdopen(r), os.fdopen(w, 'w'))

    def addReader(self, r):
        '''
        In Python 2.5 and later, you can also use the with statement.
        When used with a lock, this statement automatically
        acquires the lock before entering the block, and releases
        it when leaving the block:
        '''
        with self.mLock:
            self.mReaders[r.fileno()] = r
            self.mCondition.notify()

    def addWriter(self, w):
        with self.mLock:
            self.mWriters[w.fileno()] = w
            self.mCondition.notify()

    def removeReader(self, r):
        with self.mLock:
            self.mReadersToRemove.add(r)
            self.mCondition.notify()

    def removeWriter(self, w):
        with self.mLock:
            self.mWritersToRemove.add(w)
            self.mCondition.notify()

    def requestExit(self) :
        with self.mLock:
            self.mDone = True;
            print "write q to pipe " + str(self.mQuit[1].fileno())
            self.mQuit[1].write('q\n')
            self.mCondition.notify()

    def done(self):
        self.join()

    def exitPending(self):
        with self.mLock:
            return self.mDone

    def discontinueMonitoring(toRemove, fromDict):
            # remove any to be closed readers/writers
            removedList = []
            with self.mLock:
                while len(toRemove):
                    t = toRemove.pop()
                    del fromDict[t.fileno()]
                    removedList.append(t)

            [e.onExit() for e in removedList]

    def checkForError(inError):
        for e in inError:
            io = None
            msgs = []
            with self.mLock:
                if e in self.mReaders.keys():
                    io = self.mReaders[e]
                    msgs.append('read failed')

                if e in self.mWriters.keys():
                    io = self.mWriters[e]
                    msgs.append('write failed')

            if io is not None:
                [io.onError(msg) for msg in msgs]

    def run(self):
        while not self.exitPending() :
            # generate list of readers and writes
            readyToRead = []
            readyToWrite = []
            inError = []
            with self.mLock :
                readers = [rf for rf in self.mReaders.keys()]
                writers = [wf for wf in self.mWriters.keys()]

                if len(readers) == 0 and len(writers) == 0 :
                    sys.stderr.write("waiting for reader/writer\n")
                    self.mCondition.wait()
                    sys.stderr.write("out of wait, signal received\n")
                    continue

                print "append quit fileno " + str(self.mQuit[0].fileno())
                readers.append(self.mQuit[0].fileno())
                print readers

            #without mLock
            print "wait in select"
            readyToRead, readyToWrite, nError = select.select(readers,
                                                              writers,
                                                              [e for e in set(readers).union(writers)])
            print "out of select"
            self.checkForError(inError)

            for r in readyToRead :
                self.mReaders[r].onRead()

            for w in readyToWrite:
                self.mWriters[w].onWrite()

            self.discontinueMonitoring(self.mReadersToRemove, self.mReaders)
            self.discontinueMonitoring(self.mWritersToRemove, self.mWriters)

        self.discontinueMonitoring(set(self.mReaders), self.mReaders)
        self.discontinueMonitoring(set(self.mWriters), self.mWriters)

class SocketListener(IO):
    def __init__(self, s, i):
        self.mSocket = s
        self.mIOMonitor = i
        pass

    def onRead(self):
        sys.stderr.write("socket onRead, abstract\n")
        pass

    def onWrite(self):
        sys.stderr.write("socket onWrite, abstract\n")
        pass

    def fileno(self):
        return self.mSocket.fileno()

class ClientHandler(SocketListener):
    def __init__(self, s, i):
        SocketListener.__init__(self, s, i)
        self.mMode = 0x3
        self.mIOMonitor.addReader(self)
        #self.mIOMonitor.addWriter(c)

    def onRead(self):
        # read from the socket, FIXME
        chunk = self.mSocket.recv(5)

        if len(chunk) == 0:
            sys.stderr.write('ClientHandler: recv -> 0, remove\n')
            self.mIOMonitor.removeReader(self)
        else:
            sys.stderr.write('ClientHandler: %s\n' % (chunk))

        #FIXME
    def onWrite(self):
        pass #self.mSocket.send("hello")

    def onError(self, msg):
        sys.stderr.write('ClientHandler: %s\n' % (msg))

    def onExit(self):
        print "ClientHandler onExit"
        self.mSocket.close()

class ConnectionListener(SocketListener):
    def __init__(self, s, i):
        SocketListener.__init__(self, s, i)

    def onRead(self):
        (clientSocket, address) = self.mSocket.accept()
        c = ClientHandler(clientSocket, self.mIOMonitor)
        sys.stderr.write("got connection %s\n" % (str(address)))

    def onError(self, msg):
        sys.stderr.write('ConnectionListener: %s\n' %(msg))

    def onExit(self):
        print "ConnectionListener onExit"
        self.mSocket.close()

# set socket as non-blocking
def createServer() :
    serverSocket = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    serverSocket.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    serverSocket.bind((socket.gethostname(), 8080))
    serverSocket.listen(5)
    return serverSocket

def xmain2():
    mIOMonitor = IOMonitor()
    mIOMonitor.start()
    mServerSocket = createServer()
    mConnectionListener = ConnectionListener(mServerSocket, mIOMonitor)
    mIOMonitor.addReader(mConnectionListener)
    c=raw_input(">")
    mIOMonitor.requestExit()
    mIOMonitor.done()

def xmain():
    mSocket = createServer()
    while (1) :
        sys.stderr.write("waiting connection\n")
        (clientSocket, address) = mSocket.accept()
        sys.stderr.write("got connection %s\n" % (str(address)))
        chunk = clientSocket.recv(5)
        sys.stderr.write("client sends %s\n" % (chunk))
        break

if __name__ == "__main__":
    xmain2()
