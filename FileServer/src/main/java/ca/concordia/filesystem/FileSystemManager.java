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
    }


    public void deleteFile(String fileName) throws Exception {

        int fileFEntryIndex = findFileFEntryIndex(fileName);

        writeZeroes(fileFEntryIndex);

        inodeTable[fileFEntryIndex] = null;
    }

    public int findNextFreeBlockIndex(int currentIndex){

        for (int i = currentIndex; i < MAXBLOCKS; i++){
            if (freeBlockList[i] == true){
                return i;
            }
        }
        return -1;
    }

    public int numFreeBlocks(){

        int freeBlocks = 0;

        for (int i = 0; i < MAXBLOCKS; i++){

            if (freeBlockList[i] == true){
                freeBlocks++;
            }
        }

        return freeBlocks;
    }
    
    public void writeZeroes(int fileIndex) throws Exception{

        int blockIndex = inodeTable[fileIndex].getFirstBlock();
        int numOfFileBlocks = (int) Math.ceil(inodeTable[fileIndex].getFilesize() / BLOCK_SIZE);
        int numOfFileBlocksWritten = 0;
        byte[] zeroes = new byte[BLOCK_SIZE];
        int temp;

        while(numOfFileBlocksWritten < numOfFileBlocks){
            disk.seek(blockIndex * BLOCK_SIZE);
            disk.write(zeroes);

            temp = blockIndex;
            blockIndex = fnodeTable[temp].getNext();

            fnodeTable[temp].setBlockIndex(-temp);
            fnodeTable[temp].setNext(-1);
            freeBlockList[temp] = true;
            
            numOfFileBlocksWritten++;
        }

        inodeTable[fileIndex].setFilesize((short) 0);
    };

    public int findFileFEntryIndex(String fileName) throws Exception{
        
        for (int i = 0; i < MAXFILES; i++){

            if (inodeTable[i].getFilename() == fileName){

                return i;

            }else if (i == MAXFILES - 1){

                throw new Exception("ERROR: file " + fileName + "does not exist\n");
            }
        }

        return -1;
    }

    public void writeFile(String fileName, byte[] contents) throws Exception {

        int fileFEntryIndex = findFileFEntryIndex(fileName);
        int freeBlocks = numFreeBlocks();
        
        int numOfCurrentFileBlocks = (int) Math.ceil(inodeTable[fileFEntryIndex].getFilesize() / BLOCK_SIZE);
        int numOfFutureFileBlocks = (int) Math.ceil(contents.length / BLOCK_SIZE);

        if (freeBlocks + numOfCurrentFileBlocks >= numOfFutureFileBlocks){

            writeZeroes(fileFEntryIndex);

            int blockIndex = inodeTable[fileFEntryIndex].getFirstBlock();
            int start = 0;
            int end = Math.min(contents.length, BLOCK_SIZE);
            int numOfBlocksWritten = 0;

            for (int i = 0; i < MAXBLOCKS; i++){

                if (freeBlockList[i] == true && numOfBlocksWritten < numOfFutureFileBlocks){

                    byte[] slice = Arrays.copyOfRange(contents, start, end);
                    disk.seek(blockIndex * BLOCK_SIZE);
                    disk.write(slice);
                    
                    blockIndex = findNextFreeBlockIndex(i);
                    fnodeTable[i].setBlockIndex(i);

                    if (numOfBlocksWritten != numOfFutureFileBlocks - 1) {fnodeTable[i].setNext(blockIndex);}

                    start = end;
                    end = Math.min(contents.length, end + BLOCK_SIZE);
                    freeBlockList[i] = false;
                    numOfBlocksWritten++;
                }
            }
            
            inodeTable[fileFEntryIndex].setFilesize((short) contents.length);
        }
        else{
            throw new Exception("ERROR: file too large!\n");
        }        
    }
    
    public byte[] readFile(String fileName) throws Exception {

        int fileIndex = findFileFEntryIndex(fileName);
        int fileSize = inodeTable[fileIndex].getFilesize();
        int blockIndex = inodeTable[fileIndex].getFirstBlock();
        int numBlocksInFile = (int) Math.ceil(fileSize / BLOCK_SIZE);

        byte[] contents = new byte[fileSize];
        byte[] bytesToRead = new byte[BLOCK_SIZE];

        for (int i = 0; i < numBlocksInFile; i++){

            disk.seek(blockIndex * BLOCK_SIZE);

            if (i == numBlocksInFile - 1){

                bytesToRead = new byte[fileSize % BLOCK_SIZE];
            }

            disk.read(bytesToRead);

            System.arraycopy(bytesToRead, 0, contents, i * BLOCK_SIZE, bytesToRead.length);
            blockIndex = fnodeTable[blockIndex].getNext();
        }        
        
        return contents;
    }

    public String[] listFiles(){
        
        int numFiles = inodeTable.length;
        String[] files = new String[numFiles];

        for (int i = 0; i < numFiles; i++){

            files[i] = inodeTable[i].getFilename();
        }

        return files;
    }
}
