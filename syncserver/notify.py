from fsevents import Observer, Stream
from fsevents import IN_CREATE
import os
import time
import sys
import signal

CREATED = IN_CREATE

class Context :
    def __init__(self, o, s):
        self.observer = o
        self.stream = s

    def wait(self):
        self.observer.join()

    def start(self):
        self.observer.start()
        self.observer.schedule(stream)

    def stop(self):
        self.observer.unschedule(stream)
        self.observer.stop()

    def interrupt(self):
        self.stop()

#http://stackoverflow.com/questions/631441/interruptible-thread-join-in-python
#threads and signals do not interact well?
def handler(signum, frame):
    sys.stderr.write("interrupted\n")
    [c.interrupt() for c in Context.s]

# simple struct, not a new-style-class
class Meta:
    def __init__(self):
        self.state = CREATED
        self.size = 0
        self.hash = 0
        self.id = uniqueId++

items = dict() #name -> meta { lastKnownState, size, hash, id}
lookup = dict() #id -> items
uniqueId = 1

def callback(fe):
    sys.stderr.write("%d %s\n" % (fe.mask, fe.name))
    #check if fe.name is in items

if __name__ == "__main__":
#    signal.signal(signal.SIGINT, handler)
    observer = Observer()
    stream = Stream(callback, os.getcwd() + "/scan", file_events=True)

    c = Context(observer, stream)
    c.start()
    raw_input("Press any key to exit\n")
    c.stop()
    c.wait()
    sys.stderr.write("all done\n")
