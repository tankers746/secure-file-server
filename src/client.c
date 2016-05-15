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



#define PORT 1342
#define LENGTH 1024 
char *filePath = "/Users/tom/Desktop/audio-vga.m4v";
char *serverAddr = "192.168.15.108";


void error(const char *msg)
{
	perror(msg);
	exit(1);
}

char *sendMetaData(char *path, int sock) {
	char *name;
	int size;
	char buffer[LENGTH];
	
	struct stat st;
	stat(path, &st);
	size = htonl(st.st_size);
	name = basename(path);
	
	if(send(sock, &size, sizeof(size), 0) < 0) {
		fprintf(stderr, "ERROR: Failed to send file size. (errno = %d)\n", errno);
	}

	//memset(buffer, '\0', LENGTH);
	strcpy(buffer, name);
	if(send(sock, buffer, sizeof(buffer), 0) < 0) {
		fprintf(stderr, "ERROR: Failed to send file name. (errno = %d)\n", errno);
	}
	
	return name;
}


int main(int argc, char *argv[])
{
	/* Variable Definition */
	int sockfd; 
	int nsockfd;
	char revbuf[LENGTH]; 
	struct sockaddr_in remote_addr;
	

	/* Get the Socket file descriptor */
	if ((sockfd = socket(AF_INET, SOCK_STREAM, 0)) == -1)
	{
		fprintf(stderr, "ERROR: Failed to obtain Socket Descriptor! (errno = %d)\n",errno);
		exit(1);
	}

	/* Fill the socket address struct */
	remote_addr.sin_family = AF_INET; 
	remote_addr.sin_port = htons(PORT); 
	inet_pton(AF_INET, serverAddr, &remote_addr.sin_addr); 
	memset(&(remote_addr.sin_zero),'\0',8);

	/* Try to connect the remote */
	if (connect(sockfd, (struct sockaddr *)&remote_addr, sizeof(struct sockaddr)) == -1)
	{
		fprintf(stderr, "ERROR: Failed to connect to the host! (errno = %d)\n",errno);
		exit(1);
	}
	else 
		printf("[Client] Connected to server at port %d...ok!\n", PORT);

	/* Send File to Server */
	//if(!fork())
	//{
		char sdbuf[LENGTH]; 
		FILE *fs = fopen(filePath, "r");
		if(fs == NULL)
		{
			printf("ERROR: File %s not found.\n", filePath);
			exit(1);
		}

		char *fileName = sendMetaData(filePath, sockfd);


		printf("[Client] Sending %s to the Server... ", fileName);

		memset(sdbuf, '\0', LENGTH);
		int fs_block_sz; 
		while((fs_block_sz = fread(sdbuf, sizeof(char), LENGTH, fs)) > 0)
		{
		    if(send(sockfd, sdbuf, fs_block_sz, 0) < 0)
		    {
		        fprintf(stderr, "ERROR: Failed to send file %s. (errno = %d)\n", fileName, errno);
		        break;
		    }
		    memset(sdbuf, '\0', LENGTH);
		}
		printf("Ok File %s from Client was Sent!\n", fileName);
	//}
	

	/* Receive File from Server */
	printf("[Client] Receiveing file from Server and saving it as final.txt...");
	char* fr_name = "/home/aryan/Desktop/progetto/final.txt";
	FILE *fr = fopen(fr_name, "a");
	if(fr == NULL)
		printf("File %s Cannot be opened.\n", fr_name);
	else
	{
		memset(revbuf, '\0', LENGTH);
		int fr_block_sz = 0;
	    while((fr_block_sz = recv(sockfd, revbuf, LENGTH, 0)) > 0)
	    {
			int write_sz = fwrite(revbuf, sizeof(char), fr_block_sz, fr);
	        if(write_sz < fr_block_sz)
			{
	            error("File write failed.\n");
	        }
			memset(revbuf, '\0', LENGTH);
			if (fr_block_sz == 0 || fr_block_sz != 512) 
			{
				break;
			}
		}
		if(fr_block_sz < 0)
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
	}
	close(sockfd);
	printf("[Client] Connection lost.\n");
	return (0);
}
