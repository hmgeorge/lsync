import os
import sys
import threading
import time
import Queue
'''
Having seen the mechanics behind the iterator protocol, it is easy to add iterator behavior to your classes.
Define an __iter__() method which returns an object with a next() method (raise StopIteration when iteration ends).
If the class defines next(), then __iter__() can just return self:
'''

'''
generators (yield) are an alternate option. create iter and next automatically
'''

class WorkerThread(threading.Thread):
    def __init__(self, name):
        threading.Thread.__init__(self)
        self.mName = name
        self.mDone = False
        self.mQueue = Queue.Queue() 

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

def xmain():
    w = WorkerThread("w1")
    w.start()
    time.sleep(1)
    w.enq('a')
    w.enq('b')
    w.enq('c')
    time.sleep(2)
    w.done()

if __name__ == "__main__":
    xmain()
