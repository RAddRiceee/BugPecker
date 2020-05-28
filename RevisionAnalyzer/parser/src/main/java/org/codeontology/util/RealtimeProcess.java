package org.codeontology.util;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;

public class RealtimeProcess{
    // 是否在执行
    private boolean isRunning = false;
    // 存放命令行
    private ArrayList<RealtimeProcessCommand> commandList = new ArrayList<RealtimeProcessCommand>();
    // 保存所有的输出信息
    private StringBuffer mStringBuffer = new StringBuffer();
    private ProcessBuilder mProcessBuilder = null;
    private BufferedReader readStdout = null;
    private BufferedReader readStderr = null;

    private int resultCode = 0;
    private String ROOT_DIR = null;
    private String tmp1 = null;
    private String tmp2 = null;

    public void setCommand(String ...commands){
        // 遍历命令
        for(String cmd : commands){
            RealtimeProcessCommand mRealtimeProcessCommand = new RealtimeProcessCommand();
            if(ROOT_DIR != null)
                mRealtimeProcessCommand.setDirectory(ROOT_DIR);
            mRealtimeProcessCommand.setCommand(cmd);
            commandList.add(mRealtimeProcessCommand);
        }
    }
    public void setDirectory(String directory){
        this.ROOT_DIR = directory;
    }
    public void start() throws IOException, InterruptedException{
        isRunning = true;
        for(RealtimeProcessCommand mRealtimeProcessCommand : commandList){
            System.out.println("exec cmd: " + mRealtimeProcessCommand.getCommand());
            if(ROOT_DIR != null)
                exec(Runtime.getRuntime().exec(mRealtimeProcessCommand.getCommand(), null, new File(mRealtimeProcessCommand.getDirectory())));
            else
                exec(Runtime.getRuntime().exec(mRealtimeProcessCommand.getCommand()));
        }
    }
    public String getAllResult(){
        return mStringBuffer.toString();
    }

    private void exec(final Process process) throws InterruptedException{
        // 获取标准输出
        readStdout = new BufferedReader(new InputStreamReader(process.getInputStream()));
        // 获取错误输出
        readStderr = new BufferedReader(new InputStreamReader(process.getErrorStream()));
        // 创建线程执行
        Thread execThread = new Thread(){
            public void run(){
                try {
                    // 逐行读取
                    while((tmp1 = readStdout.readLine()) != null || (tmp2 = readStderr.readLine()) != null){
                        if(tmp1 != null){
                            mStringBuffer.append(tmp1 + "\n");
                            System.out.println(tmp1);
                        }
                        if(tmp2 != null){
                            mStringBuffer.append(tmp2 + "\n");
                            System.out.println(tmp2);
                        }
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
                resultCode = process.exitValue();
            }
        };
        execThread.start();
        execThread.join();
        isRunning = false;
        System.out.println("process finish.");
    }
    public boolean isRunning(){
        return this.isRunning;
    }
    public int getCommandSize(){
        return commandList.size();
    }
    public RealtimeProcessCommand getRealtimeProcessCommand(int p){
        return commandList.get(p);
    }

}
class RealtimeProcessCommand{
    private String directory = null;
    private String command = null;
    public RealtimeProcessCommand(){}

    public void setDirectory(String directory){
        this.directory = directory;
    }
    public void setCommand(String command){
        this.command = command;
    }
    public String getDirectory(){
        return this.directory;
    }
    public String getCommand(){
        return this.command;
    }

}