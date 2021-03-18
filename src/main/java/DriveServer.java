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

    private final int port = 1234;
    private File dirroot;
    private Path currentDir;
    private Path rootDir;
    private DataInputStream dis;
    private DataOutputStream dos;
    private DataOutputStream send;
    private ObjectOutputStream oos;
    private ObjectInputStream ois;

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
                dis = new DataInputStream(socketcon.getInputStream());
                dos = new DataOutputStream(socketcon.getOutputStream());
                oos = new ObjectOutputStream(socketcon.getOutputStream());
                ois = new ObjectInputStream(socketcon.getInputStream());
                send = new DataOutputStream(socketcon.getOutputStream());

                while(true){
                    dos.writeUTF(currentDir.toString());
                    dos.flush();
                    String action = dis.readUTF();
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

                dis.close();
                dos.close();
                oos.close();
                ois.close();
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
                dos.writeUTF("Directorio eliminado correctamente");
                dos.flush();
            }else{
                if (fileDelete.delete()){
                    dos.writeUTF("Archivo eliminado correctamente");
                    dos.flush();
                }else{
                    dos.writeUTF("Error eliminando archivo");
                    dos.flush();
                }
            }
        }else{
            dos.writeUTF("Archivo/directorio no encontrado");
            dos.flush();
        }
    }

    public void downloadFiles(String action) throws IOException {
        String[] split_action = action.split(" ");
        String file_name = split_action[1];
        Path filepath = Paths.get(currentDir.toString(), file_name);
        File fileDownload = new File(filepath.toString());
        if(fileDownload.exists()){
            if(fileDownload.isDirectory()){
                dos.writeUTF("dirok");
                dos.flush();
                if(dis.readBoolean()){
                    System.out.println("Mandando una carpeta");
                    String zip_name = fileDownload.getAbsolutePath()+".zip";
                    System.out.println(zip_name);
                    ZipUtil.pack(fileDownload, new File(zip_name));
                    sendFile(new File(zip_name), "DIR-ZIP");
                    new File(zip_name).delete();
                    //dis.readUTF();
                }
            }else{
                dos.writeUTF("fileok");
                dos.flush();
                if (dis.readBoolean()){
                    sendFile(fileDownload, "NO-DIR");
                }
            }
        }else{
            dos.writeUTF("404");
            dos.flush();
        }
    }

    public void uploadFiles() throws IOException, ClassNotFoundException {
        if(dis.readBoolean()){
            Archivo porRecibir = (Archivo) ois.readObject();
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
        DataInputStream fileInput = new DataInputStream(new FileInputStream(fileDownload.getAbsolutePath()));
        oos.writeObject(porEnviar);
        oos.flush();
        long enviado = 0;
        int parte=0, porcentaje=0;
        while (enviado<porEnviar.getSize()){
            byte[] bytes = new byte[1500];
            parte = fileInput.read(bytes);
            dos.write(bytes, 0, parte);
            dos.flush();
            enviado = enviado + parte;
            porcentaje = (int)((enviado*100)/porEnviar.getSize());
            System.out.println("\rEnviado el "+porcentaje+" % del archivo");
        }
        System.out.println("Deje de enviar");
        String response = dis.readUTF();
    }

    public void receiveFile(Archivo porRecibir, String ruta) throws IOException, ClassNotFoundException {
        System.out.println("Recibiendo "+ porRecibir.getNombre() + " de tamanio "+ porRecibir.getSize());
        DataOutputStream fileout = new DataOutputStream(new FileOutputStream(ruta));
        long recibido = 0;
        int parte = 0, porcentaje = 0;
        while (recibido<porRecibir.getSize()){
            byte[] bytes = new byte[1500];
            parte = dis.read(bytes);
            fileout.write(bytes, 0, parte);
            fileout.flush();
            recibido = recibido+parte;
            porcentaje = (int)((recibido*100)/porRecibir.getSize());
            System.out.println("Recibido el "+ porcentaje +" % del archivo");
        }
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
            dos.writeUTF("Directorio "+ dir + " creado");
            dos.flush();
        }else{
            System.out.println("El directorio "+ dir + " ya existe");
            dos.writeUTF("El directorio "+ dir + " ya existe");
            dos.flush();
        }
    }

    public void changeDir(String action) throws IOException {
        String[] action_split = action.split(" ");
        String dir = action_split[1];
        Path new_path = Paths.get(currentDir.toString(), dir);
        File newDir = new File(new_path.toString());
        if (newDir.exists()) {
            dos.writeUTF("ok");
            dos.flush();
            currentDir = Paths.get(new_path.toString());
        } else {
            dos.writeUTF("El directorio " + dir + " no existe");
            dos.flush();
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
        oos.writeObject(files);
        oos.flush();
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
