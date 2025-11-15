package ca.concordia.filesystem;

import ca.concordia.filesystem.datastructures.FEntry;
import ca.concordia.filesystem.datastructures.FNode;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.Arrays;
import java.util.concurrent.locks.ReentrantLock;

public class FileSystemManager {

    private static final int MAXFILES = 5;
    private static final int MAXBLOCKS = 10;
    private static FileSystemManager instance;
    private RandomAccessFile disk;
    private final ReentrantLock globalLock = new ReentrantLock();

    private static final int BLOCK_SIZE = 128; // Example block size
    private static final int FENTRY_SIZE = 15;
    private static final int FNODE_SIZE = 8;
    private static final int METADATA_SIZE = (MAXFILES * FENTRY_SIZE) + (FNODE_SIZE * MAXBLOCKS);

    private FEntry[] inodeTable = new FEntry[MAXFILES]; // Array of inodes
    private FNode[] fnodeTable = new FNode[MAXBLOCKS];
    private boolean[] freeBlockList = new boolean[MAXBLOCKS]; // Bitmap for free blocks

    public FileSystemManager(String filename, int totalSize) {
        // Initialize the file system manager with a file
        if(instance == null) {

            try {

                disk = new RandomAccessFile("filesystem", "rw");
                

                // Initialize FNode table
                int numOfMetadataBlocks = (int) Math.ceil(METADATA_SIZE / BLOCK_SIZE);

                for (int i = 0; i < MAXBLOCKS; i++){

                    if (numOfMetadataBlocks == 1){

                        if(i == 0){
                            fnodeTable[i] = new FNode(i);
                            freeBlockList[i] = false;
                        }
                        else{

                            fnodeTable[i] = new FNode(-i);
                            freeBlockList[i] = true;
                        }
                    }
                    else{
                        
                        if (i < numOfMetadataBlocks){

                            fnodeTable[i] = new FNode(i);
                            if (i != numOfMetadataBlocks - 1) {fnodeTable[i].setNext(i + 1);}
                            freeBlockList[i] = false;
                        }
                        else{
                            
                            fnodeTable[i] = new FNode(-i);
                            freeBlockList[i] = true;
                        }
                    }

                }
            
                // Initialize FEntry table
                for (int i = 0; i < MAXFILES; i++){
                    inodeTable[i] = null;
                }

            } catch (IOException e){
                e.printStackTrace();
            }

            //TODO Initialize the file system

        } else {
            throw new IllegalStateException("FileSystemManager is already initialized.\n");
        }

    }

    public void createFile(String fileName) throws Exception {

        for (int i = 0; i < MAXFILES; i++){

            if (inodeTable[i].getFilename() == fileName){
                throw new Exception("This file already exists\n");
            }
        }

        for (int i = 0; i < MAXFILES; i++){

            if (inodeTable[i] == null){
                inodeTable[i] = new FEntry(fileName, (short) 0, (short) (i + 1));
                fnodeTable[i + 1].setBlockIndex(i + 1);
                // Even if the file is empty, the freeBlockList should still reflect that the block is being used by a file
                freeBlockList[i + 1] = false;
                break;
            }
            else if (i == MAXFILES - 1){
                throw new Exception("Filesystem is full!\n");
            }
            else{
                continue;
            }
        }

        // throw new UnsupportedOperationException("Method not implemented yet.");
    }


    public void deleteFile(String fileName) throws Exception {

        for (int i = 0; i < MAXFILES; i++){

            if(inodeTable[i].getFilename() == fileName){


                inodeTable[i] = null;
            }

        }


        // TODO
        // throw new UnsupportedOperationException("Method not implemented yet.");
    }

    public int findNextFreeBlockIndex(int currentIndex){

        for (int i = currentIndex; i < MAXBLOCKS; i++){
            if (freeBlockList[i] == true){
                return i;
            }
        }

        return -1;
    }

    public void writeFile(String fileName, byte[] contents) throws Exception {

        int freeBlocks = 0;
        int numOfFileblocks = (int) Math.ceil(contents.length / BLOCK_SIZE);

        for (int i = 0; i < MAXBLOCKS; i++){

            if (freeBlockList[i] == true){
                freeBlocks++;
            }
        }

        int firstBlockIndex = 0;

        if (freeBlocks >= numOfFileblocks){

            for (int i = 0; i < MAXFILES; i++){

                if (inodeTable[i].getFilename() == fileName){

                    firstBlockIndex = fnodeTable[inodeTable[i].getFirstBlock()].getBlockIndex();

                }else if (i == MAXFILES - 1){

                    throw new Exception("ERROR: file " + fileName + "does not exist\n");
                }
            }

            int fileIndex = firstBlockIndex;
            int start = 0;
            int end = Math.min(contents.length, BLOCK_SIZE);
            int numOfBlocksWritten = 0;
            int nextFreeBlockIndex;

            for (int i = 0; i < MAXBLOCKS; i++){

                if (freeBlockList[i] == true && numOfBlocksWritten < numOfFileblocks){

                    byte[] slice = Arrays.copyOfRange(contents, start, end);
                    disk.seek(fileIndex * BLOCK_SIZE);
                    disk.write(slice);
                    
                    start = end;
                    end = Math.min(contents.length, end + BLOCK_SIZE);

                    nextFreeBlockIndex = findNextFreeBlockIndex(i);
                    
                    fnodeTable[i].setBlockIndex(i);
                    if (numOfBlocksWritten != numOfFileblocks - 1) {fnodeTable[i].setNext(nextFreeBlockIndex);}
                    freeBlockList[i] = false;
                    
                    numOfBlocksWritten++;
                }
            }
        }
        else{
            throw new Exception("ERROR: file too large!\n");
        }        

        // TODO
        //throw new UnsupportedOperationException("Method not implemented yet.");
    }
    
    public byte[] readFile(String fileName) throws Exception {
        // TODO
        //throw new UnsupportedOperationException("Method not implemented yet.");
    }

    public String[] listFiles() throws Exception {
        // TODO
        //throw new UnsupportedOperationException("Method not implemented yet.");
    }
}
