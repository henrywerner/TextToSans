package sanser;

import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import java.io.*;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.Locale;

public class SoundBuilder {
    private File trimmedInputFile;
    private final int speechMode;
    private final double inputTrimDuration;

    SoundBuilder() {
        speechMode = 0;
        inputTrimDuration = 1.000;
    }

    SoundBuilder(int mode, double duration) {
        speechMode = mode;
        inputTrimDuration = duration;
    }

    public String buildSound(File outputDir, File inputFile, String scriptText) {
        try {
            //helpText.setText("Saving...");

            //adjust input and output
            File outputFile = new File(outputDir.getAbsolutePath() + "//generatedSpeech.wav");
            int byteLimit = getByteLimit(inputFile);
            trimmedInputFile = trimAudio(inputFile, byteLimit);

            ArrayList<Character> sounds = new ArrayList<>();
            String[] words = scriptText.split("\\s+"); //split on spaces, include consecutive

            if (speechMode == 0) { //beep on every character
                for (String s : words) {
                    boolean endsInPunctuation = false;
                    int beepCount = s.length();
                    char a = s.charAt(beepCount-1);
                    //check if the word ends in any of these characters
                    if (a=='.' || a==',' || a=='!' || a=='?' || a==':' || a==';' || a=='-') {
                        beepCount--;
                        endsInPunctuation = true;
                    }
                    while (beepCount > 0) {
                        sounds.add('a');
                        beepCount--;
                    }

                    sounds.add(' '); //add pause after every word

                    if (endsInPunctuation)
                        sounds.add(' '); //add an extra pause if the word ends in punctuation
                }
            }
            else { //beep on every syllable
                for (String s : words) {
                    int syl = getSyllables(s);
                    while (syl != 0) {
                        sounds.add('a');
                        syl--;
                    }
                    char a = s.charAt(s.length()-1);
                    if (a=='.' || a==',' || a=='!' || a=='?' || a==':' || a==';' || a=='-'){
                        sounds.add(' ');
                    }
                    sounds.add(' '); // add a pause after every word
                }
            }

            createWav(outputFile.getAbsolutePath(), byteLimit, sounds.toArray(sounds.toArray(new Character[0])));
            return outputFile.getAbsolutePath();
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    int getSyllables(String str) {
        str = str.toLowerCase(Locale.ROOT); //convert to lowercase
        boolean previouslyVowel = false;
        int syllables = 0;

        for (int x = 0; x < str.length(); x++) {
            char c = str.charAt(x);
            if (c=='a'|| c=='e' || c=='i' || c=='o' || c=='u' || c=='y') {
                if (!previouslyVowel) {
                    syllables++;
                    previouslyVowel = true;
                }
            }
            else //consonant
                previouslyVowel = false;
        }

        if (str.endsWith("e"))
            syllables--;

        if (syllables == 0)
            return 1;
        return syllables;
    }

    int getByteLimit(File in) {
        AudioInputStream input = null;
        int byteLimit = 0;
        try {
            //get bytes per frame so we can convert to seconds
            input = AudioSystem.getAudioInputStream(in);
            int bytesPerFrame = input.getFormat().getFrameSize();
            float frameRate = input.getFormat().getFrameRate();
            float bytesPerSec = frameRate * bytesPerFrame;
            byteLimit = (int)(bytesPerSec * inputTrimDuration); //set the trim to the seconds duration set by user
            System.out.println("Byte Limit: " + byteLimit);
        } catch (Exception e) {
            e.printStackTrace();
            return 0;
        } finally {
            try { if (input != null) input.close(); } catch(IOException e) {e.printStackTrace();}
        }
        return byteLimit;
    }

    /**
     * trim audio file to a set length
     * @param in input file
     * @param byteLimit limited bytes per second of playback
     * @return trimmed audio file
     */
    File trimAudio(File in, int byteLimit) {
        File outFile = new File("./data/input_trimmed.wav");
        if (!outFile.getParentFile().exists())
            outFile.getParentFile().mkdirs();
        if (!outFile.exists()) {
            try {
                outFile.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        OutputStream os = null;
        InputStream is = null;

        try {
            os = new FileOutputStream(outFile);
            is = new FileInputStream(in);

            byte[] tempBuffer = new byte[1024];
            int nRed = 0;
            int count = 0;
            int counterLimit = byteLimit / 1024; //TODO: make sure this isn't causing the cutoff glitch

            // Copy all contents of wav to out.wav
            while ((nRed = is.read(tempBuffer)) != -1 && count <= counterLimit) { //TODO: add 44 to counterLimit if count==0. this should prevent clipping off the header?
                os.write(tempBuffer, 0, nRed);
                os.flush();
                count++;
            }

            updateFileHeader(outFile.getAbsolutePath(), 1);

        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try { if (os != null) os.close(); } catch(IOException e) {System.out.println("trimAudio Output Stream wasn't closed");}
            try { if (is != null) is.close(); } catch(IOException e) {System.out.println("trimAudio Input Stream wasn't closed");}
        }

        return outFile;
    }

    /**
     * Create WAV file based on a character array of syllables
     * @param out Output WAV file location
     * @param byteLimit limited bytes per second of playback
     * @param dialogue Array of characters corresponding to syllables (should probably just be a bool array)
     * @throws IOException In case something goes wrong with reading/writing files
     */
    public void createWav(String out, int byteLimit, Character[] dialogue) throws IOException {
        //helpText.setText("Building file...");

        File outFile = new File(out);
        if (!outFile.getParentFile().exists())
            outFile.getParentFile().mkdirs();
        if (!outFile.exists())
            outFile.createNewFile();

        //initialize all streams
        OutputStream os = null;
        ByteArrayOutputStream baos = null;
        InputStream is = null;
        InputStream is2 = null;

        //skip to the first non-space because I need to handle the first syllable outside of the loop.
        int startPoint = 0;
        while (dialogue[startPoint] == ' ') {
            startPoint++;
        }

        try {
            os = new FileOutputStream(outFile);
            baos = new ByteArrayOutputStream();

            //create an input stream from the trimmed file and output to the outfile.
            is = new FileInputStream(trimmedInputFile);
            byte[] tempBuffer = new byte[1024];
            int nRed = 0;
            while ((nRed = is.read(tempBuffer)) != -1) {
                os.write(tempBuffer, 0, nRed);
                os.flush();
            }
            startPoint++; //update startPoint again.

            //make a second inputStream because we need to read from the beginning of the file.
            is2 = new FileInputStream(trimmedInputFile);
            is2.transferTo(baos);

            //loop through the array of syllables. Start at the adjusted start point.
            for (int i = startPoint; i < dialogue.length; i++) {
                switch (dialogue[i]) {
                    case 'e': //make specific noise
                        //add code later?
                        break;
                    case ' ': //pause
                        for (int x = 0; x < byteLimit; x++) {
                            os.write(0);
                        }
                        os.flush();
                        break;
                    default: //make generic noise
                        InputStream dupe = new ByteArrayInputStream(baos.toByteArray()); //create duplicate of trimmed input
                        dupe.skip(44);
                        byte[] buffer = new byte[1024];
                        nRed = 0;
                        while ((nRed = dupe.read(buffer)) != -1) {
                            os.write(buffer, 0, nRed); //append dupe to the end of output wav file
                            os.flush();
                        }
                        dupe.close();
                }
            }
            os.flush(); //just in case?
            os.close();

            updateFileHeader(out, dialogue.length);
        } finally {
            //close all streams
            try { if (os != null) os.close(); } catch(IOException e) {System.out.println("createWav OS wasn't closed");}
            try { if (baos != null) baos.close(); } catch(IOException e) {System.out.println("createWav BAOS wasn't closed");}
            try { if (is != null) is.close(); } catch(IOException e) {System.out.println("createWav IS wasn't closed");}
            try { if (is2 != null) is2.close(); } catch(IOException e) {System.out.println("createWav IS2 wasn't closed");}
        }
    }

    /* FOLLOWING CODE TAKEN FROM https://programmersought.com/article/66957226714/ */

    public static int byteArrayToInt(byte[] b) {
        return b[3] & 0xFF | (b[2] & 0xFF) << 8 | (b[1] & 0xFF) << 16 | (b[0] & 0xFF) << 24;
    }

    public static byte[] intToByteArray(int a) {
        return new byte[]{(byte) ((a >> 24) & 0xFF), (byte) ((a >> 16) & 0xFF), (byte) ((a >> 8) & 0xFF), (byte) (a & 0xFF)};
    }

    public static byte[] byteToByte(byte[] a) {
        if (a.length == 4)
            return new byte[]{a[3], a[2], a[1], a[0]};
        return null;
    }
    /* END OF TAKEN CODE */

    /**
     * Updates the file header of the output wav file to reflect the increased duration
     * @param out Output file location
     * @param fileCount Amount of times the sample file was used
     * @throws IOException If the output file cannot be accessed
     */
    public void updateFileHeader(String out, int fileCount) {
        RandomAccessFile raf = null;
        FileChannel channel = null;

        try {
            raf = new RandomAccessFile(out, "rw");
            channel = raf.getChannel(); //open file channel

            MappedByteBuffer buffer = channel.map(FileChannel.MapMode.READ_WRITE, 0, 44);// File header length
            //noinspection ConstantConditions
            int length1 = byteArrayToInt(byteToByte(new byte[]{buffer.get(4), buffer.get(5), buffer.get(6), buffer.get(7)}));
            //noinspection ConstantConditions
            int length2 = byteArrayToInt(byteToByte(new byte[]{buffer.get(40), buffer.get(41), buffer.get(42), buffer.get(43)}));

            int headerLength1 = 0, headerLength2 = 0;
            //this works because the pause duration is the same length as the trimmed sample
            while (fileCount > 0) {
                headerLength1 += length1;
                headerLength2 += length2;
                fileCount--;
            }

            //this next part is unchanged from what I found online...
            byte[] head1 = byteToByte(intToByteArray(headerLength1));
            byte[] head2 = byteToByte(intToByteArray(headerLength2));
            // Perform modification operations
            buffer.put(4, head1[0]);
            buffer.put(5, head1[1]);
            buffer.put(6, head1[2]);
            buffer.put(7, head1[3]);
            buffer.put(40, head2[0]);
            buffer.put(41, head2[1]);
            buffer.put(42, head2[2]);
            buffer.put(43, head2[3]);
            buffer.force();//Forced output, changes in the buffer take effect to the file

            buffer.clear();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try { if (channel != null) channel.close(); } catch(IOException e) {System.out.println("updateFileHeader channel wasn't closed");}
            try { if (raf != null) raf.close(); } catch(IOException e) {System.out.println("updateFileHeader raf wasn't closed");}
        }
    }


}
