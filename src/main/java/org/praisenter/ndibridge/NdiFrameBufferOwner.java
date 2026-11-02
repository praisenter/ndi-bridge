package org.praisenter.ndibridge;

interface NdiFrameBufferOwner {
    void release(NdiAbstractFrame frame);
}