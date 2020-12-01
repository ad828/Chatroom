package server;
import org.apache.commons.lang3.*;
public class Milestone2 {

	public static void main (String[] args) {
		String message1 = "*hi* wow /hi/ wow! _hi_ wow!!";
if (message1.contains("*")){
		System.out.print(converttobold(message1));
}if (message1.contains("/")){
		System.out.print(converttoItalic(message1));
}if (message1.contains("_")){
		System.out.print(converttoUnderline(message1));
	}
}
	public static String converttobold(String message) {
		String words = StringUtils.substringBetween(message, "*");
		String Strnew= "<b>" + words + "</b>";
		return Strnew;
	}
	public static String converttoItalic(String message) {
		String words = StringUtils.substringBetween(message, "/");
		String Strnew= "<i>" + words + "</i>";
		return Strnew;
	}
	public static String converttoUnderline(String message) {
		String words = StringUtils.substringBetween(message, "_");
		String Strnew= "<u>" + words + "</u>";
		return Strnew;
	}
	
}
