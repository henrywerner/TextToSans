package sanser;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.Slider;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.text.Text;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;

import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import java.io.*;
import java.net.URL;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Locale;
import java.util.ResourceBundle;

public class Controller implements Initializable {
    public File inputFile;
    public File trimmedInputFile;
    public File outputDir;
    public File outputFile;

    public int byteLimit;
    public double inputTrimDuration = 1.000; //TODO: Add UI element that allows the user to adjust this

    //used for the borrowed code. Rework this after testing.
    private int headLength1 = 0;
    private int headLength2 = 0;

    @FXML private Button btnInput;
    @FXML private Button btnOutput;
    @FXML private Text inputPath, outputPath;
    @FXML private TextArea script;
    @FXML private TextField trimInput;
    @FXML private Slider trimSlider;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        trimInput.setText("" + inputTrimDuration);
        trimSlider.setValue(inputTrimDuration);
    }

    public void selectInputFile(ActionEvent event) {
        FileChooser wavChooser = new FileChooser();
        wavChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("WAV file", "*.wav"));

        inputFile = wavChooser.showOpenDialog(null);

        if (inputFile != null) {
            //System.out.println(inputFile.getName());
            inputPath.setText("Input File: " + inputFile.getAbsolutePath());
        }
        else {
            System.out.println("Null file");
            inputPath.setText("Input File: ...");
        }
    }

    public void selectOutputFile(ActionEvent event) {
        DirectoryChooser chooser1 = new DirectoryChooser();
        chooser1.setInitialDirectory(new File("./src"));

        outputDir = chooser1.showDialog(null);

        if (outputDir != null)
            outputPath.setText("Output Dir: " + outputDir.getAbsolutePath());
        else
            outputPath.setText("Output Dir: ...");
    }

    public void adjustTrimSlider(ActionEvent event) {
        inputTrimDuration = trimSlider.valueProperty().doubleValue();
        trimInput.setText("" + inputTrimDuration);
    }

    public void adjustTrimInput(ActionEvent event) {
        double k;
        try {
            k = Double.parseDouble(trimInput.getText());
        } catch (NumberFormatException | NullPointerException e) {
            k = 1.000;
        }
        trimSlider.setValue(k);
        inputTrimDuration = k;

    }

    public void updateAdjustmentField(double i) {
        //handle adjustment in percent of the original length. I.e. full length = 1.0
        trimInput.setText("" + i);
        trimSlider.setValue(i);
    }

    public void save(ActionEvent event) {
        if (buildSound())
            System.out.println("Saved");
        else
            System.out.println("Something went wrong.");
    }

    public int getSyllables(String str) {
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

    public boolean buildSound() {
        //get text from script entry box
        String scriptText = script.getText();

        try {
            //adjust input and output
            outputFile = new File(outputDir.getAbsolutePath() + "//generatedSpeech.wav");
            trimmedInputFile = trimAudio(inputFile);

            //String[] wavList = new String[10];
            ArrayList<String> wavList = new ArrayList<>();

            //Parse through script
            /*
            for (int c = 0; c < scriptText.length(); c++) {
                switch (scriptText.charAt(c)){
                    case ' ':
                        wavList.add("pause");
                        break;
                    default:
                        wavList.add(trimmedInput.getAbsolutePath());
                }
            }
            */
            ArrayList<Character> sounds = new ArrayList<>();
            String[] words = scriptText.split("\\s+"); //split on spaces, include consecutive
            System.out.println(words);
            for (String s : words) {
                int syl = getSyllables(s);
                while (syl != 0) {
                    sounds.add('a');
                    syl--;
                }
                if (s.matches(".*([.,!?])\\z")){
                    sounds.add(' ');
                }

                sounds.add(' '); // add a pause after every word
            }

//            for (int x = 0; x < 10; x++) {
//                wavList[x] = trimmedInput.getAbsolutePath();
//            }
            //addWav(outputFile.getAbsolutePath(), wavList.toArray(new String[0])); //THIS PROBABLY WONT WORK
            createWav(outputFile.getAbsolutePath(), sounds.toArray(sounds.toArray(new Character[0])));
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public AudioInputStream append(AudioInputStream sound1, AudioInputStream sound2) {
        AudioInputStream appendedFiles = new AudioInputStream(
                new SequenceInputStream(sound1, sound2),
                sound1.getFormat(),
                sound1.getFrameLength() + sound2.getFrameLength());

        return appendedFiles;
    }

    public void updateByteLimit(File in) {
        try {
            //get bytes per frame so we can convert to seconds
            AudioInputStream input = AudioSystem.getAudioInputStream(in);
            int bytesPerFrame = input.getFormat().getFrameSize();
            float frameRate = input.getFormat().getFrameRate();
            float bytesPerSec = frameRate * bytesPerFrame;
            byteLimit = (int)(bytesPerSec * inputTrimDuration); //set the trim to the seconds duration set by user
            System.out.println("Byte Limit: " + byteLimit);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Trim audio file to a specified length
     * @param in input audio file
     * @return shortened version of input file
     * @throws IOException in case there is an issue writing the file to disk
     */
    public File trimAudio(File in) throws IOException {
        File outFile = new File("./input_trimmed.wav");
        if (!outFile.getParentFile().exists())
            outFile.getParentFile().mkdirs();
        if (!outFile.exists())
            outFile.createNewFile();

        //update byteLimit if undef
        if (byteLimit == 0)
            updateByteLimit(in);

        OutputStream os = new FileOutputStream(outFile);
        InputStream is = new FileInputStream(in);

        byte[] tempBuffer = new byte[1024];
        int nRed = 0;
        int count = 0;
        int counterLimit = byteLimit / 1024;

        // Copy all contents of wav to out.wav
        while ((nRed = is.read(tempBuffer)) != -1 && count <= counterLimit) {
            os.write(tempBuffer, 0, nRed);
            os.flush();
            count++;
        }
        is.close();
        os.close();

        updateFileHead(in.getAbsolutePath(), false);
        updateFileHead(outFile.getAbsolutePath(), true);//Head synthesis

        return outFile;
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

    //TODO: Rewrite this so it is optimized for using a single file 50+ times
    public void updateFileHead(String out, boolean ifUpdate) throws IOException {
        RandomAccessFile raf = new RandomAccessFile(out, "rw");
        //long fileLength = raf.length();
        // Open a file channel
        FileChannel channel = raf.getChannel();
        // A certain part of the data in the mapping file is stored in the memory in read and write mode
        MappedByteBuffer buffer = channel.map(FileChannel.MapMode.READ_WRITE, 0, 44);// File header length
        //noinspection ConstantConditions
        int length1 = byteArrayToInt(byteToByte(new byte[]{buffer.get(4), buffer.get(5), buffer.get(6), buffer.get(7)}));
        //noinspection ConstantConditions
        int length2 = byteArrayToInt(byteToByte(new byte[]{buffer.get(40), buffer.get(41), buffer.get(42), buffer.get(43)}));
        // modify the header file
        if (ifUpdate) {
            byte[] head1 = byteToByte(intToByteArray(headLength1));
            byte[] head2 = byteToByte(intToByteArray(headLength2));
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
        } else {
            headLength1 = headLength1 + length1;
            headLength2 = headLength2 + length2;
        }
        buffer.clear();
        channel.close();
        raf.close();
    }

    /**
     * Combine multiple wavs into a new wav. Original code by yujing, modified by me :)
     * @param out output file
     * @param in input file array
     * @throws IOException
     */
    public void addWav(String out, String... in) throws IOException {
        File outFile = new File(out);
        if (!outFile.getParentFile().exists())
            outFile.getParentFile().mkdirs();
        if (!outFile.exists())
            outFile.createNewFile();

        OutputStream os = new FileOutputStream(outFile);

        for (int i = 0; i < in.length; i++) {
            if (in[i].equals("pause")) {
                //this might be the WORST way I could have done this, but it works.
                for (int x = 0; x < byteLimit; x++) {
                    os.write(0);
                }
                os.flush();
            }
            else {
                File file1 = new File(in[i]);
                InputStream is = new FileInputStream(file1);
                if (i != 0) {
                    //noinspection ResultOfMethodCallIgnored
                    is.skip(44);// Skip the file header of the following .wav
                }
                byte[] tempBuffer = new byte[1024];
                int nRed = 0;
                // Copy all contents of wav to out.wav
                while ((nRed = is.read(tempBuffer)) != -1) {
                    os.write(tempBuffer, 0, nRed);
                    os.flush();
                }
                is.close();
            }
        }
        os.flush(); //just in case?
        os.close();
        // At this point, all wavs in the in array are merged into out.wav, but when out.wav is played, the audio content is still only the content of the first audio, so the file header of out.wav needs to be changed
        for (String s : in) updateFileHead(s, false);
        updateFileHead(out, true);//Head synthesis
    }
    /* END OF TAKEN CODE */

    /**
     * Create WAV file based on a character array of syllables
     * @param out Output WAV file location
     * @param dialogue Array of characters corresponding to syllables (should probably just be a bool array)
     * @throws IOException In case something goes wrong with reading/writing files
     */
    public void createWav(String out, Character[] dialogue) throws IOException {
        File outFile = new File(out);
        if (!outFile.getParentFile().exists())
            outFile.getParentFile().mkdirs();
        if (!outFile.exists())
            outFile.createNewFile();

        OutputStream os = new FileOutputStream(outFile);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        //skip to the first non-space because I need to handle the first syllable outside of the loop.
        int startPoint = 0;
        while (dialogue[startPoint] == ' ') {
            startPoint++;
        }

        //create an input stream from the trimmed file and output to the outfile.
        InputStream is = new FileInputStream(trimmedInputFile);
        byte[] tempBuffer = new byte[1024];
        int nRed = 0;
        while ((nRed = is.read(tempBuffer)) != -1) {
            os.write(tempBuffer, 0, nRed);
            os.flush();
        }
        is.close();
        startPoint++; //update startPoint again.

        //make a second inputStream because we need to read from the beginning of the file.
        InputStream is2 = new FileInputStream(trimmedInputFile);
        is2.transferTo(baos);
        is2.close();

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

        // At this point, all wavs in the in array are merged into out.wav, but when out.wav is played, the audio
        // content is still only the content of the first audio, so the file header of out.wav needs to be changed
        headLength1 = 0;
        headLength2 = 0;
        for (int x = 0; x < dialogue.length; x++) //this is what adjusts the values used to update the file header.
            updateFileHead(trimmedInputFile.getAbsolutePath(), false);
        updateFileHead(out, true);//Head synthesis

        // NOTE: THE ISSUE WHERE THE END IS RANDOMLY CUT OFF IS PROBABLY CAUSED BY THE HEADER LENGTH NOT ACCOUNTING FOR
        // THE PAUSES. OR MAYBE IT'S AN ISSUE WITH THE WORD COUNT??
    }
}
