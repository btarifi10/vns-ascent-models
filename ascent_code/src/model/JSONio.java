/*
The copyrights of this software are owned by Duke University.
Please refer to the LICENSE and README.md files for licensing instructions.
The source code can be found on the following GitHub repository: https://github.com/wmglab-duke/ascent
*/

package model;

import java.io.*;
import java.util.Scanner;
import org.json.JSONObject;

@SuppressWarnings({ "path" })
public class JSONio {

    public static JSONObject read(String filepath) throws FileNotFoundException {
        return new JSONObject(new Scanner(new File(filepath)).useDelimiter("\\A").next());
    }

    public static void write(String filepath, JSONObject data) {
        try {
            BufferedWriter writer = new BufferedWriter(new FileWriter(filepath));
            writer.write(data.toString());
            writer.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
