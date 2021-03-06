package org.swdc.plugin;

import javafx.application.Platform;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

import javax.inject.Inject;
import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;

/**
 * 这个用来给windows下GraalVM生成的
 * 可执行文件添加图标。
 */
@Mojo(name = "gen-icon")
public class GenIconExecutable extends AbstractMojo {

    @Parameter(property = "target")
    private String filePath;

    @Parameter(property = "iconPath")
    private String iconPath;

    @Inject
    private MavenProject project;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        String os = System.getProperty("os.name");
        if (!os.toLowerCase().startsWith("win")) {
            getLog().info("不是window系统。");
            return;
        }
        try {
            String baseDir = project.getBasedir().getPath();
            File lib = new File(baseDir + "/" + "GraalNatives.dll");
            if (!lib.exists()) {
                InputStream in = this.getClass().getModule()
                        .getResourceAsStream("GraalNatives.dll");
                OutputStream out = Files.newOutputStream(lib.toPath());
                in.transferTo(out);
                in.close();
                out.close();
            }
            // 加载JNI，调用WindowsAPI实现图标添加。
            System.load(lib.getAbsolutePath());

            File targetFolder = new File(project.getBuild().getOutputDirectory())
                    .getAbsoluteFile()
                    .getParentFile();

            File targetFile = targetFolder.toPath()
                    .resolve(filePath)
                    .toFile();

            File iconFile = new File(project.getFile().getAbsoluteFile().getParent() + File.separator + iconPath);
            if (targetFile.exists() && iconFile.exists()) {
                boolean result = PlatformNatives.addWindowsIcon(targetFile.getAbsolutePath(),iconFile.getAbsolutePath());
                if (!result) {
                    getLog().error("失败，无法添加icon。");
                } else {
                    getLog().info("添加Icon成功。");
                }
            } else {
                if (!iconFile.exists()) {
                    getLog().error(iconFile.getAbsolutePath() + " 不存在。");
                }
                if (!targetFile.exists()) {
                    getLog().error(targetFile.getAbsolutePath() + " 不存在。");
                }
            }
        } catch (Exception e) {
            getLog().error(e);
        }
    }

}
