package sanser;

import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.control.Button;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.media.AudioClip;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.scene.text.Text;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;

import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import java.awt.*;
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

    private int speechMode;

    public AudioClip clip;
    private boolean soundGenerated = false;
    private boolean playingAudio = false;

    public int byteLimit;
    public double inputTrimDuration = 1.000; //TODO: Add UI element that allows the user to adjust this

    //used for the borrowed code. Rework this after testing.
    private int headLength1 = 0;
    private int headLength2 = 0;

    @FXML private Button btnInput;
    @FXML private Button btnOutput;
    @FXML private Button btnSave;
    @FXML private Button btnPlay;
    @FXML private Button btnDevPlayS;
    @FXML private Button btnDevGoto;
    @FXML private Button btnDevClear;
    @FXML private Text inputPath, outputPath, helpText;
    @FXML private TextArea script;
    @FXML private TextField trimInput;
    @FXML private Slider trimSlider;
    @FXML private ChoiceBox<String> boxSpeechType;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        trimInput.setText("" + inputTrimDuration);
        trimSlider.setValue(inputTrimDuration);

        outputDir = new File("");
        outputPath.setText("Output Dir: " + outputDir.getAbsolutePath());

        helpText.setText("");

        //set up speech type selector
        boxSpeechType.getItems().add("Each Character");
        boxSpeechType.getItems().add("Each Syllable");
        boxSpeechType.getSelectionModel().select(0);
        speechMode = 0;

        //update speechMode when selector is changed
        boxSpeechType.setOnAction(e -> {
            speechMode = boxSpeechType.getSelectionModel().getSelectedIndex();
        });

        //adjust the inputTrimDuration whenever the slider is changed
        trimSlider.valueProperty().addListener(new ChangeListener<Number>() {
            @Override
            public void changed(ObservableValue<? extends Number> observableValue, Number number, Number t1) {
                int round = (int)(t1.doubleValue() * 100);
                inputTrimDuration = ((double)round)/100;
                trimInput.setText("" + inputTrimDuration);
                helpText.setText("Sample trim set: " + inputTrimDuration);
            }
        });
    }

    public void selectInputFile(ActionEvent event) {
        FileChooser wavChooser = new FileChooser();
        wavChooser.setInitialDirectory(new File("./"));
        wavChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("WAV file", "*.wav"));

        inputFile = wavChooser.showOpenDialog(null);

        if (inputFile != null) {
            //System.out.println(inputFile.getName());
            inputPath.setText("Input File: " + inputFile.getAbsolutePath());
            helpText.setText("Input file set: " + inputFile.getName());
        }
        else {
            System.out.println("Null file");
            inputPath.setText("Input File: ...");
            helpText.setText("Input file not chosen.");
        }
    }

    public void selectOutputFile(ActionEvent event) {
        DirectoryChooser chooser1 = new DirectoryChooser();
        chooser1.setInitialDirectory(new File("./"));

        outputDir = chooser1.showDialog(null);

        if (outputDir != null)
            outputPath.setText("Output Dir: " + outputDir.getAbsolutePath());
        else
            outputPath.setText("Output Dir: ...");

        helpText.setText("Output directory set: " + outputDir.getAbsolutePath());
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
        helpText.setText("Sample trim set: " + inputTrimDuration);
    }

    public void save(ActionEvent event) {
        SoundBuilder sb = new SoundBuilder(speechMode, inputTrimDuration);

        String outFile = sb.buildSound(outputDir, inputFile, script.getText());

        if (outFile != null) {
            soundGenerated = true;
            System.out.println("Saved");
            helpText.setText("Save Complete.");
        }
        else {
            soundGenerated = false;
            System.out.println("Something went wrong.");
            helpText.setText("Save Failed.");
        }
    }

    public void dev_goToFile(ActionEvent event) {
        Desktop d = Desktop.getDesktop();

        try {
            if (outputFile != null)
                Runtime.getRuntime().exec("explorer.exe /select," + outputFile.getAbsolutePath());
            else
                d.open(new File (outputDir.getAbsolutePath()));
        } catch (Exception e) {
            e.printStackTrace();
            helpText.setText("path not found");
        }
    }

    public void dev_clear(ActionEvent event) {
        inputFile = null;
        outputFile = null;
        trimmedInputFile = null;
        outputDir = null;
    }

    public void playSound(ActionEvent event) {
        if (soundGenerated && !playingAudio) {
            /*
            Media media = new Media(new File(outputFile.getAbsolutePath()).toURI().toString());
            mp = new MediaPlayer(media);
            //mp.setAutoPlay(true);
            mp.play();
            btnPlay.setText("Pause");

            mp.setOnEndOfMedia(() -> {
                btnPlay.setText("Play");
            });
             */
            clip = new AudioClip(new File(outputFile.getAbsolutePath()).toURI().toString());
            clip.play();
            //btnPlay.setText("Pause");
        }
        else if (playingAudio) {
            //mp.stop();
            clip.stop();
            //btnPlay.setText("Play");
        }

    }

    public void dev_playSound(ActionEvent event) {
        clip = new AudioClip(new File(inputFile.getAbsolutePath()).toURI().toString());
        clip.play();
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
            helpText.setText("Saving...");

            //adjust input and output
            outputFile = new File(outputDir.getAbsolutePath() + "//generatedSpeech.wav");
            trimmedInputFile = trimAudio(inputFile);

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
                    if (s.matches(".*([.,!?])\\z")){
                        sounds.add(' ');
                    }
                    sounds.add(' '); // add a pause after every word
                }
            }

            createWav(outputFile.getAbsolutePath(), sounds.toArray(sounds.toArray(new Character[0])));
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public void updateByteLimit(File in) {
        AudioInputStream input = null;

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
        } finally {
            try { if (input != null) input.close(); } catch(IOException e) {e.printStackTrace();}
        }
    }

    /**
     * Trim audio file to a specified length
     * @param in input audio file
     * @return shortened version of input file
     * @throws IOException in case there is an issue writing the file to disk
     */
    public File trimAudio(File in) {
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

        //update byteLimit if undef
        if (byteLimit == 0)
            updateByteLimit(in);

        OutputStream os = null;
        InputStream is = null;

        try {
            os = new FileOutputStream(outFile);
            is = new FileInputStream(in);

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

            //updateFileHead(in.getAbsolutePath(), false);
            //updateFileHead(outFile.getAbsolutePath(), true);//Head synthesis
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

    public void updateFileHead(String out, boolean ifUpdate) {
        RandomAccessFile raf = null;
        FileChannel channel = null;

        try {
            raf = new RandomAccessFile(out, "rw");
            // Open a file channel
            channel = raf.getChannel();
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
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try { if (channel != null) channel.close(); } catch(IOException e) {System.out.println("updateFileHead channel wasn't closed");}
            try { if (raf != null) raf.close(); } catch(IOException e) {System.out.println("updateFileHead raf wasn't closed");}
        }
    }
    /* END OF TAKEN CODE */

    /**
     * Create WAV file based on a character array of syllables
     * @param out Output WAV file location
     * @param dialogue Array of characters corresponding to syllables (should probably just be a bool array)
     * @throws IOException In case something goes wrong with reading/writing files
     */
    public void createWav(String out, Character[] dialogue) throws IOException {
        helpText.setText("Building file...");

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

        /*
        // At this point, all wavs in the in array are merged into out.wav, but when out.wav is played, the audio
        // content is still only the content of the first audio, so the file header of out.wav needs to be changed
        headLength1 = 0;
        headLength2 = 0;
        for (int x = 0; x < dialogue.length; x++) //this is what adjusts the values used to update the file header.
            updateFileHead(trimmedInputFile.getAbsolutePath(), false);
        updateFileHead(out, true);//Head synthesis

        // NOTE: THE ISSUE WHERE THE END IS RANDOMLY CUT OFF IS PROBABLY CAUSED BY THE HEADER LENGTH NOT ACCOUNTING FOR
        // THE PAUSES. OR MAYBE IT'S AN ISSUE WITH THE WORD COUNT??

         */
    }
}
