package ca.concordia.filesystem;

import ca.concordia.filesystem.datastructures.FEntry;
import ca.concordia.filesystem.datastructures.FNode;

import java.io.IOException;
import java.io.RandomAccessFile;
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
            throw new IllegalStateException("FileSystemManager is already initialized.");
        }

    }

    public void createFile(String fileName) throws Exception {
 
        if (inodeTable[MAXFILES - 1] != null){
            throw new Exception("Too many files in filesystem");
        }
        else{

            for (int i = 1; i < MAXFILES; i++){
                if (inodeTable[i] == null){
                    inodeTable[i] = new FEntry(fileName, (short) 0, (short) i);

                    
                    break;
                }
                else{
                    continue;
                }
            }
        }

        // throw new UnsupportedOperationException("Method not implemented yet.");
    }


    public void deleteFile(String fileName) throws Exception {
        // TODO
        throw new UnsupportedOperationException("Method not implemented yet.");
    }

    public void writeFile(String fileName) throws Exception {
        // TODO
        throw new UnsupportedOperationException("Method not implemented yet.");
    }
    
    public byte[] readFile(String fileName) throws Exception {
        // TODO
        throw new UnsupportedOperationException("Method not implemented yet.");
    }

    public String[] listFiles() throws Exception {
        // TODO
        throw new UnsupportedOperationException("Method not implemented yet.");
    }
}
