package sanser;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.TextArea;
import javafx.scene.text.Text;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;

import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import java.io.*;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;

public class Controller {
    public File inputFile;
    public File outputDir;
    public File outputFile;

    public float inputTrimDuration = 0.13f; //TODO: Add UI element that allows the user to adjust this

    //used for the borrowed code. Rework this after testing.
    private int headLength1 = 0;
    private int headLength2 = 0;

    @FXML
    private Button btnInput;

    @FXML
    private Button btnOutput;

    @FXML
    private Text inputPath, outputPath;

    @FXML
    private TextArea script;

    public void selectInputFile(ActionEvent event) {
        FileChooser wavChooser = new FileChooser();
        wavChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("WAV file", "*.wav"));
        wavChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("MP3 file", "*.mp3"));

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

    public void save(ActionEvent event) {
        if (buildSound())
            System.out.println("Saved");
        else
            System.out.println("Something went wrong.");
    }

    public boolean buildSound() {
        //get text from script entry box
        String scriptText = script.getText();

        try {
            /*
            AudioInputStream clip1 = AudioSystem.getAudioInputStream(inputFile);
            AudioInputStream clip2 = AudioSystem.getAudioInputStream(inputFile);

            AudioInputStream appendedFiles = append(clip1, clip2);

            for (int x = 0; x < 10; x++) {
                appendedFiles = append(appendedFiles, clip1);
            }

            outputFile = new File(outputDir.getAbsolutePath() + "//generatedSpeech.wav");

            AudioSystem.write(appendedFiles,
                    AudioFileFormat.Type.WAVE,
                    outputFile);
            */

            //Testing functionality of borrowed code...
            headLength1 = 0;
            headLength2 = 0;
            outputFile = new File(outputDir.getAbsolutePath() + "//generatedSpeech.wav");
            File trimmedInput = trimAudio(inputFile);

            String[] wavList = new String[10];
            for (int x = 0; x < 10; x++) {
                wavList[x] = trimmedInput.getAbsolutePath();
            }
            addWav(outputFile.getAbsolutePath(), wavList);

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

    public File trimAudio(File in) throws IOException {
        File outFile = new File("./input_trimmed.wav");
        if (!outFile.getParentFile().exists()) outFile.getParentFile().mkdirs();
        if (!outFile.exists()) outFile.createNewFile();

        int byteLimit;

        try {
            //get bytes per frame so we can convert to seconds
            AudioInputStream input = AudioSystem.getAudioInputStream(in);
            int bytesPerFrame = input.getFormat().getFrameSize();
            float frameRate = input.getFormat().getFrameRate();
            float bytesPerSec = frameRate * bytesPerFrame;
            byteLimit = (int)(bytesPerSec * inputTrimDuration); //set the trim to the seconds duration set by user
            System.out.println("Byte Limit: " + byteLimit);
        } catch (Exception e) {
            byteLimit = 70560; //this is an arbitrary limit. It probably will break things. remove this.
            e.printStackTrace();
        }

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

    /* CODE TAKEN FROM https://programmersought.com/article/66957226714/ */

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
     * Combine multiple wavs into a new wav
     * @param out output file
     * @param in input file array
     * @throws IOException
     */
    public void addWav(String out, String... in) throws IOException {
        File outFile = new File(out);
        if (!outFile.getParentFile().exists()) outFile.getParentFile().mkdirs();
        if (!outFile.exists()) outFile.createNewFile();
        OutputStream os = new FileOutputStream(outFile);// append
        for (int i = 0; i < in.length; i++) {
            File file1 = new File(in[i]);
            //System.out.println(in[i] + "File length:" + file1.length());
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
        os.close();
        // At this point, all wavs in the in array are merged into out.wav, but when out.wav is played, the audio content is still only the content of the first audio, so the file header of out.wav needs to be changed
        for (String s : in) updateFileHead(s, false);
        updateFileHead(out, true);//Head synthesis
    }
    /* END OF TAKEN CODE */
}
