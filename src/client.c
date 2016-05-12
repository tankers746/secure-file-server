#include <sys/socket.h>
#include <sys/types.h>
#include <netinet/in.h>
#include <netdb.h>
#include <stdio.h>
#include <string.h>
#include <stdlib.h>
#include <unistd.h>
#include <errno.h>
#include <arpa/inet.h> 

int port = 1342;
char filename[] = "/Users/tom/Desktop/whichbus.c";

int main(int argc, char *argv[])
{
	int sockfd = 0, n = 0, connfd = 0;
	char recvBuff[1024];
	char sendBuff[1025];
	
	FILE *inputFile = fopen(filename, "wb");
	if(inputFile == NULL)
	{
	  fprintf(stderr, "Something went south!");
	  return 1;
	}	

	struct sockaddr_in serv_addr; 

	if(argc < 2)
	{
		printf("\n Usage: %s <ip of server> \n",argv[0]);
		return 1;
	} 

	memset(recvBuff, '0', sizeof(recvBuff));
	memset(sendBuff, '0', sizeof(sendBuff)); 
	if((sockfd = socket(AF_INET, SOCK_STREAM, 0)) < 0)
	{
		printf("\n Error : Could not create socket \n");
		return 1;
	} 

	memset(&serv_addr, '0', sizeof(serv_addr)); 

	serv_addr.sin_family = AF_INET;
	serv_addr.sin_port = htons(port); 

	if(inet_pton(AF_INET, argv[1], &serv_addr.sin_addr)<=0)
	{
		printf("\n inet_pton error occured\n");
		return 1;
	} 

	if( connect(sockfd, (struct sockaddr *)&serv_addr, sizeof(serv_addr)) < 0)
	{
	   printf("\n Error : Connect Failed \n");
	   return 1;
	}
	printf("Connected to %s:%i\n",argv[1],port);

	//send file
	int bytesRead = fread(sendBuff, sizeof(sendBuff), 1, inputFile);
	while (!feof(inputFile))
	{
		send(sockfd, sendBuff, bytesRead, 0);
		bytesRead = fread(sendBuff, sizeof(sendBuff), 1, inputFile);
		printf("%i bytes sent\i",(int)sizeof(sendBuff));
	} 
	printf("Sent file %s\n",filename);
	close(sockfd);

	if(n < 0)
	{
		printf("\n Read error \n");
	} 

	return 0;
}
