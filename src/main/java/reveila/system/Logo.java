package reveila.system;

/*
String[] logo = new String[] {
" _____                    _       ",
"|  __ '.               _ | |      ",
"| |__) |_____   _____ (_)| | __ _ ",
"|  _  ./ _ \ \ / / _ \| || |/ _' |",
"| | \ \  __/\ ' /  __/| || | |_) |",
"|_|  \_\___| \_/ \___||_||_|\_.|_|",
"Version 1.0.0"

};
*/
public class Logo {

public static void print() {

String[] logo = new String[] {
" _____                    _       ",
"|  __ '.               _ | |      ",
"| |__) |_____   _____ (_)| | __ _ ",
"|  _  ./ _ \\ \\ / / _ \\| || |/ _' |",
"| | \\ \\  __/\\ ' /  __/| || | |_) |",
"|_|  \\_\\___| \\_/ \\___||_||_|\\_.|_|",
"Version 1.0.0"
};

System.out.println();
for (String line : logo) {
    System.out.println(line);
}
System.out.println();
}

}