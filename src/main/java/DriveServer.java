import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Locale;

import net.lingala.zip4j.ZipFile;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.zeroturnaround.zip.ZipUtil;


public class DriveServer {

    private final int port = 8383;
    private File dirroot;
    private Path currentDir;
    private Path rootDir;
    private ObjectInputStream ois;
    private ObjectOutputStream oos;
    //private Socket socketcon;

    public DriveServer() {

        createRootDir();

        System.out.println("Iniciando servidor...");
        ServerSocket ss = null;

        try {
            ss = new ServerSocket(port);
            ss.setReuseAddress(true);
            System.out.println("Server iniciado, esperando por clientes");

            while (true) {
                Socket socketcon = ss.accept();
                System.out.println("Cliente conectado desde " + socketcon.getInetAddress() + ":" + socketcon.getPort());
                oos = new ObjectOutputStream(socketcon.getOutputStream());
                oos.flush();
                ois = new ObjectInputStream(socketcon.getInputStream());

                while(true){
                    sendMessage(currentDir.toString());
                    String action = receiveMessage();
                    if(action.equals("ls")) listFiles();
                    if(action.equals("cd ..")) backDir();
                    if(action.matches("cd \\w+")) changeDir(action);
                    if(action.matches("mkdir \\w+")) createDir(action);
                    if(action.equals("upload")) uploadFiles();
                    if(action.matches("download (\\w+\\.?(\\w+)?)")) downloadFiles(action);
                    if(action.matches("delete (\\w+\\.?(\\w+)?)")) deleteFiles(action);
                    if(action.equals("exit")){
                        break;
                    }
                }

                socketcon.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }

    }

    public void deleteFiles(String action) throws IOException {
        String[] split_action = action.split(" ");
        String file_name = split_action[1];
        Path filepath = Paths.get(currentDir.toString(), file_name);
        File fileDelete = new File(filepath.toString());
        if(fileDelete.exists()){
            if(fileDelete.isDirectory()){
                FileUtils.deleteDirectory(fileDelete);
                sendMessage("Directorio eliminado correctamente");
            }else{
                if (fileDelete.delete()){
                    sendMessage("Archivo eliminado correctamente");
                }else{
                    sendMessage("Error eliminando archivo");
                }
            }
        }else{
            sendMessage("Archivo/directorio no encontrado");
        }
    }

    public void downloadFiles(String action) throws IOException {
        String[] split_action = action.split(" ");
        String file_name = split_action[1];
        Path filepath = Paths.get(currentDir.toString(), file_name);
        File fileDownload = new File(filepath.toString());
        if(fileDownload.exists()){
            if(fileDownload.isDirectory()){
                sendMessage("dirok");
                if(receiveBoolean()){
                    System.out.println("Mandando una carpeta");
                    String zip_name = fileDownload.getAbsolutePath()+".zip";
                    System.out.println(zip_name);
                    ZipUtil.pack(fileDownload, new File(zip_name));
                    sendFile(new File(zip_name), "DIR-ZIP");
                    new File(zip_name).delete();
                    //dis.readUTF();
                }
            }else{
                sendMessage("fileok");
                if (receiveBoolean()){
                    sendFile(fileDownload, "NO-DIR");
                }
            }
        }else{
            sendMessage("404");
        }
    }

    public void uploadFiles() throws IOException, ClassNotFoundException {
        if(receiveBoolean()){
            Archivo porRecibir = (Archivo) receiveObject();
            String ruta = Paths.get(currentDir.toString(), porRecibir.getNombre()).toString();
            if(!porRecibir.getExt().equals("DIR")){
                receiveFile(porRecibir, ruta);
            }else{
                System.out.println("Recibiendo una carpeta");
                receiveFile(porRecibir, ruta);
                Path destino = Paths.get(currentDir.toString(),FilenameUtils.removeExtension(porRecibir.getNombre()) );
                System.out.println("Descomprimiendo " + ruta + " en " + destino.toString());
                new ZipFile(ruta).extractAll(destino.toString());
                new File(ruta).delete();
            }
        }
    }

    public void sendFile(File fileDownload, String type) throws IOException {
        Archivo porEnviar = new Archivo();
        porEnviar.setNombre(fileDownload.getName());
        porEnviar.setSize(fileDownload.length());
        if(type.equals("DIR-ZIP")) porEnviar.setExt("DIR");
        else porEnviar.setExt(FilenameUtils.getExtension(fileDownload.getName()).toUpperCase(Locale.ROOT));

        sendObject(porEnviar);

        DataInputStream fileInput = new DataInputStream(new FileInputStream(fileDownload.getAbsolutePath()));


        long tam = porEnviar.getSize();
        long enviados = 0;
        int l;

        while (enviados<tam){
            byte[] b = new byte[1500];
            l = fileInput.read(b);
            oos.write(b, 0, l);
            oos.flush();
            enviados += l;

        }

        fileInput.close();

        System.out.println("Archivo enviado");
    }

    public void receiveFile(Archivo porRecibir, String ruta) throws IOException, ClassNotFoundException {
        System.out.println("Recibiendo "+ porRecibir.getNombre() + " de tamanio "+ porRecibir.getSize());
        DataOutputStream fileout = new DataOutputStream(new FileOutputStream(ruta));

        long recibido = 0;
        int l;
        long tam = porRecibir.getSize();

        while(recibido<tam){
            byte[] b = new byte[1500];
            l = ois.read(b, 0, b.length);
            fileout.write(b, 0, l);
            fileout.flush();
            recibido += l;
        }

        fileout.close();
        System.out.println("Deje de recibir");
    }

    public void backDir(){
        String parent = (currentDir.getParent()).toString();
        if(parent.equals(rootDir.getParent().toString())){
            System.out.println("Ya estas en la raiz");
        }else{
            System.out.println(parent);
            currentDir = currentDir.getParent();
        }
    }

    public void createDir(String action) throws IOException {
        String[] action_split = action.split(" ");
        String dir = action_split[1];
        Path new_path = Paths.get(currentDir.toString(), dir);
        File newDir = new File(new_path.toString());
        if(!newDir.exists()){
            newDir.mkdirs();
            newDir.setWritable(true);
            System.out.println("Directorio "+ dir + " creado");
            sendMessage("Directorio "+ dir + " creado");
        }else{
            System.out.println("El directorio "+ dir + " ya existe");
            sendMessage("El directorio "+ dir + " ya existe");
        }
    }

    public void changeDir(String action) throws IOException {
        String[] action_split = action.split(" ");
        String dir = action_split[1];
        Path new_path = Paths.get(currentDir.toString(), dir);
        File newDir = new File(new_path.toString());
        if (newDir.exists()) {
            sendMessage("ok");
            currentDir = Paths.get(new_path.toString());
        } else {
            sendMessage("El directorio " + dir + " no existe");
        }
    }

    public void listFiles() throws IOException {
        ArrayList<String > files = new ArrayList<String>();
        File dir = new File(currentDir.toString());
        File[] archivos = dir.listFiles();
        for (File f : archivos) {
            String name = "";
            name = name + f.getName() +  " - " + (FilenameUtils.getExtension(f.getName())).toUpperCase(Locale.ROOT);
            if(f.isDirectory()) name = name + "DIR";
            files.add(name);
        }
        sendObject(files);
    }

    public void sendMessage(String mes){
        try {
            oos.writeUTF(mes);
            oos.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public String receiveMessage(){
        try {
            String res = ois.readUTF();
            return res;
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    public void sendObject(Object toSend){
        try {
            oos.writeObject(toSend);
            oos.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public Object receiveObject(){
        try {
            Object rec = ois.readObject();
            return rec;
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
            return null;
        }
    }

    public boolean receiveBoolean(){
        try {
            boolean res = ois.readBoolean();
            return res;
        } catch (IOException e) {

            e.printStackTrace();
            return false;
        }
    }

    public void createRootDir(){
        File f = new File("");
        String ruta = f.getAbsolutePath();
        String carpeta="archivos";
        String ruta_archivos = ruta+"/"+carpeta+"/";
        System.out.println(ruta_archivos);
        dirroot = new File(ruta_archivos);
        dirroot.mkdirs();
        dirroot.setWritable(true);
        currentDir = Paths.get(dirroot.getAbsolutePath());
        rootDir = Paths.get(dirroot.getAbsolutePath());
    }

    public static void main(String[] args) throws IOException, ClassNotFoundException {

        new DriveServer();

    }

}
