import com.maths22.directories.*;

class JarTest {
    public static void main(String[] args) {
        BaseDirectories baseDirs = BaseDirectories.get();
        System.out.println(baseDirs);

        UserDirectories userDirs = UserDirectories.get();
        System.out.println(userDirs);

        ProjectDirectories projDirs = ProjectDirectories.from("org" /*qualifier*/, "Baz Corp" /*organization*/, "Foo Bar-App" /*project*/);
        System.out.println(projDirs);
    }
}