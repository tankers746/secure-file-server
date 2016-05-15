#include <stdlib.h>
#include <stdio.h>
#include <errno.h>
#include <string.h>
#include <sys/types.h>
#include <netinet/in.h>
#include <sys/wait.h>
#include <sys/socket.h>
#include <signal.h>
#include <ctype.h>
#include <arpa/inet.h>
#include <netdb.h>
#include <unistd.h>
#include <libgen.h>
#include <sys/stat.h>
#include <limits.h>
#include <math.h>



#define LENGTH 1024
#define MIN(x, y) (((x) < (y)) ? (x) : (y))



void error(const char *msg)
{
    perror(msg);
    exit(1);
}

char *sendMetaData(char *path, int sock) {
    char *name;
    int size;
    char buffer[LENGTH];
    memset(buffer, '\0', LENGTH);
    
    struct stat st;
    stat(path, &st);
    size = htonl(st.st_size);
    name = basename(path);
    
    if(send(sock, &size, sizeof(size), 0) < 0) {
        fprintf(stderr, "ERROR: Failed to send file size. (errno = %d)\n", errno);
        //return '\0';
    }
    
    
    strcpy(buffer, name);
    if(send(sock, buffer, sizeof(buffer), 0) < 0) {
        fprintf(stderr, "ERROR: Failed to send file name. (errno = %d)\n", errno);
        //return '\0';
    }
    
    return name;
}

char *recvFileName(int sock) {
    char buffer[LENGTH];
    char *name = malloc((NAME_MAX+1) * sizeof(char));
    memset(buffer, '\0', sizeof(int));
    
    int bytesReceived = 0;
    while((bytesReceived = recv(sock, buffer, LENGTH, 0)) > 0) {
        if (bytesReceived == LENGTH)
        {
            break;
        }
    }
    if(bytesReceived <= 0)
    {
        if (errno == EAGAIN)
        {
            fprintf(stderr,"recv() timed out.\n");
            //return '\0';
        }
        else
        {
            fprintf(stderr, "recv() failed due to errno = %d\n", errno);
            //return '\0';
        }
    }

    printf("Bytes received: %i Contents of buffer: %s\n",bytesReceived,buffer);
    strcpy(name, buffer);
    return name;
    
}

int recvFileSize(int sock) {
    int size = 0;
    char intbuff[sizeof(int)];
    memset(intbuff, '\0', sizeof(int));
    
    int bytesReceived = 0;
    while((bytesReceived = recv(sock, intbuff, sizeof(int), 0)) > 0) {
        if (bytesReceived == sizeof(int))
        {
            break;
        }
    }
    if(bytesReceived <= 0)
    {
        if (errno == EAGAIN)
        {
            fprintf(stderr,"recv() timed out.\n");
            return 0;
        }
        else
        {
            fprintf(stderr, "recv() failed due to errno = %d\n", errno);
            return 0;
        }
    }
    size = ntohl(*((int*)intbuff));
    return size;
}

int sendFile(int sock, char *filePath) {
    char buffer[LENGTH];
    FILE *fs = fopen(filePath, "r");
    if(fs == NULL)
    {
        fprintf(stderr,"ERROR: File %s not found.\n", filePath);
        return EXIT_FAILURE;
    }
    
    char *fileName = sendMetaData(filePath, sock);
    if(fileName == '\0') {
        fprintf(stderr,"Failed to send file metadata\n");
        return EXIT_FAILURE;
    }
    
    printf("[Client] Sending %s to the Server...\n", fileName);
    
    memset(buffer, '\0', LENGTH);
    int fs_block_sz;
    while((fs_block_sz = fread(buffer, sizeof(char), LENGTH, fs)) > 0)
    {
        if(send(sock, buffer, fs_block_sz, 0) < 0)
        {
            fprintf(stderr, "ERROR: Failed to send file %s. (errno = %d)\n", fileName, errno);
            return EXIT_FAILURE;
        }
        memset(buffer, '\0', LENGTH);
    }
    printf("Ok File %s from Client was Sent!\n", fileName);
    return EXIT_SUCCESS;
}

int receiveFile(int sock, char * downloadsFolder) {
    
    int fileSize = recvFileSize(sock);
    char *fileName = recvFileName(sock);
    
    if(fileSize == 0 || fileName == '\0') {
        fprintf(stderr, "Error receiving file metadata from server\n");
        return EXIT_FAILURE;
    }
    
    char buffer[LENGTH];
    
    printf("Receiving %s which is %i bytes\n",fileName,fileSize);
    char filePath[PATH_MAX];
    snprintf(filePath, sizeof(filePath), "%s/%s", downloadsFolder, fileName);
    free(fileName);
    
    FILE *fr = fopen(filePath, "a");
    if(fr == NULL) {
        fprintf(stderr,"File %s Cannot be opened.\n", filePath);
        return EXIT_FAILURE;
    }
        memset(buffer, '\0', LENGTH);
        int bytesReceived = 0;
        int totalWritten = 0;
        int remaining = fileSize;
        int n = 0;
        
        while((bytesReceived = recv(sock, buffer, MIN(LENGTH, remaining), 0)) > 0)
        {
            int bytesWritten = fwrite(buffer, sizeof(char), bytesReceived, fr);
            if(bytesWritten < bytesReceived)
            {
                error("File write failed.\n");
            }
            memset(buffer, '\0', LENGTH);
            totalWritten = totalWritten + bytesWritten;
            remaining = remaining - bytesWritten;
            if(round(((double)totalWritten/(double)fileSize)*100) == n) {
                n = n+5;
                fprintf(stderr,"|");
            }
            
        }
        printf("\n");
        if(bytesReceived < 0)
        {
            if (errno == EAGAIN)
            {
                printf("recv() timed out.\n");
            }
            else
            {
                fprintf(stderr, "recv() failed due to errno = %d\n", errno);
            }
            
        }
        printf("Ok received from server!\n");
        fclose(fr);
        return EXIT_SUCCESS;
}

int connectServer(char * serverAddr, int serverPort) {
    
    struct sockaddr_in remote_addr;
    int sock;
    
    /* Get the Socket file descriptor */
    if ((sock = socket(AF_INET, SOCK_STREAM, 0)) == -1)
    {
        fprintf(stderr, "ERROR: Failed to obtain Socket Descriptor! (errno = %d)\n",errno);
        exit(EXIT_FAILURE);
    }
    
    /* Fill the socket address struct */
    remote_addr.sin_family = AF_INET;
    remote_addr.sin_port = htons(serverPort);
    inet_pton(AF_INET, serverAddr, &remote_addr.sin_addr);
    memset(&(remote_addr.sin_zero),'\0',8);
    
    /* Try to connect the remote */
    if (connect(sock, (struct sockaddr *)&remote_addr, sizeof(struct sockaddr)) == -1)
    {
        fprintf(stderr, "ERROR: Failed to connect to the host! (errno = %d)\n",errno);
        exit(EXIT_FAILURE);
    }
    else {
        
        printf("[Client] Connected to server at port %d...ok!\n", serverPort);
        return sock;
    }
    
}


int main(int argc, char *argv[])
{
    char *downloads = "/Users/tom/downloads";
    char *address = "192.168.15.108";
    char *path = "/Users/tom/desktop/audio-vga.m4v";
    
    int port = 1342;
    /* Variable Definition */
    int sockfd = connectServer(address, port);

    if(sendFile(sockfd,path) == EXIT_FAILURE) {
        fprintf(stderr, "Failed to send file to server\n");
    }
    
    if(receiveFile(sockfd, downloads) == EXIT_FAILURE) {
        fprintf(stderr, "Failed to receive file from server\n");
    }
    
    close(sockfd);
    printf("[Client] Connection lost.\n");
    exit(EXIT_SUCCESS);
}

