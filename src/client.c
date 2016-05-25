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
#include <getopt.h>
#include <fcntl.h>
#include <openssl/ssl.h>
#include <openssl/err.h>



#define LENGTH 1024
#define MIN(x, y) (((x) < (y)) ? (x) : (y))


void error(const char *msg)
{
    perror(msg);
    exit(EXIT_SUCCESS);
}

int sendRequest(SSL *ssl, char *request) {
	char buffer[LENGTH];
	memset(buffer, '\0', LENGTH);
	strcpy(buffer, request);
	if(SSL_write(ssl, buffer, sizeof(buffer)) <= 0) {
        	fprintf(stderr, "ERROR: Failed to send request. (errno = %d)\n", errno);
		return EXIT_FAILURE;
        //return '\0';
    	}
	return EXIT_SUCCESS;

}

char *sendMetaData(char *path, SSL *ssl) {
    char *name;
    int size;
    char buffer[LENGTH];
    memset(buffer, '\0', LENGTH);
    
    struct stat st;
    stat(path, &st);
    size = htonl(st.st_size);
    name = basename(path);
    
    if(SSL_write(ssl, &size, sizeof(size)) <= 0) {
        fprintf(stderr, "ERROR: Failed to send file size. (errno = %d)\n", errno);
        //return '\0';
    }
    
    
    strcpy(buffer, name);
    if(SSL_write(ssl, buffer, sizeof(buffer)) <= 0) {
        fprintf(stderr, "ERROR: Failed to send file name. (errno = %d)\n", errno);
        //return '\0';
    }
    
    return name;
}

char *recvFileName(SSL *ssl) {
    char buffer[LENGTH];
    char *name = malloc((NAME_MAX+1) * sizeof(char));
    memset(buffer, '\0', sizeof(int));
    
    int bytesReceived = 0;
    while((bytesReceived = SSL_read(ssl, buffer, LENGTH)) > 0) {
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
    strcpy(name, buffer);
    return name;
    
}

int recvFileSize(SSL *ssl) {
    int size = 0;
    char intbuff[sizeof(int)];
    memset(intbuff, '\0', sizeof(int));
    
    int bytesReceived = 0;
    while((bytesReceived = SSL_read(ssl, intbuff, sizeof(int))) > 0) {
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

int sendFile(SSL *ssl, char *filePath) {
    char buffer[LENGTH];
    FILE *fs = fopen(filePath, "r");
    if(fs == NULL)
    {
        fprintf(stderr,"ERROR: File %s not found.\n", filePath);
        return EXIT_FAILURE;
    }
    
    char *fileName = sendMetaData(filePath, ssl);
    if(fileName == '\0') {
        fprintf(stderr,"Failed to send file metadata\n");
        return EXIT_FAILURE;
    }
    
    printf("[Client] Sending %s to the Server...\n", fileName);
    
    memset(buffer, '\0', LENGTH);
    int fs_block_sz;
    while((fs_block_sz = fread(buffer, sizeof(char), LENGTH, fs)) > 0)
    {
        if(SSL_write(ssl, buffer, fs_block_sz) <= 0)
        {
            fprintf(stderr, "ERROR: Failed to send file %s. (errno = %d)\n", fileName, errno);
            return EXIT_FAILURE;
        }
        memset(buffer, '\0', LENGTH);
    }
    printf("Ok File %s from Client was Sent!\n", fileName);
    return EXIT_SUCCESS;
}

int receiveFile(SSL *ssl, char * downloadsFolder) {
    
    int fileSize = recvFileSize(ssl);
    char *fileName = recvFileName(ssl);
    
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
    
    while((bytesReceived = SSL_read(ssl, buffer, MIN(LENGTH, remaining))) > 0)
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

SSL_CTX* InitCTX(void)
{   SSL_METHOD *method;
    SSL_CTX *ctx;

    OpenSSL_add_all_algorithms();  /* Load cryptos, et.al. */
    SSL_load_error_strings();   /* Bring in and register error messages */
    method = TLSv1_2_client_method();  /* Create new client-method instance */
    ctx = SSL_CTX_new(method);   /* Create new context */
    if ( ctx == NULL )
    {
        ERR_print_errors_fp(stderr);
        abort();
    }
    return ctx;
}

void ShowCerts(SSL* ssl)
{   X509 *cert;
    char *line;

    cert = SSL_get_peer_certificate(ssl); /* get the server's certificate */
    if ( cert != NULL )
    {
        printf("Server certificates:\n");
        line = X509_NAME_oneline(X509_get_subject_name(cert), 0, 0);
        printf("Subject: %s\n", line);
        free(line);       /* free the malloc'ed string */
        line = X509_NAME_oneline(X509_get_issuer_name(cert), 0, 0);
        printf("Issuer: %s\n", line);
        free(line);       /* free the malloc'ed string */
        X509_free(cert);     /* free the malloc'ed certificate copy */
    }
    else
        printf("No certificates.\n");
}

SSL *createSSL(int sock, SSL_CTX *ctx) {
    SSL *ssl;
    SSL_library_init();

    ctx = InitCTX();
    ssl = SSL_new(ctx);      /* create new SSL connection state */
    SSL_set_fd(ssl, sock);    /* attach the socket descriptor */
    if ( SSL_connect(ssl) == -1 )   /* perform the connection */
    {
        ERR_print_errors_fp(stderr);
    	abort();
    } 
    else
    {
        printf("Connected with %s encryption\n", SSL_get_cipher(ssl));
	return ssl;
    }   

}

int connectServer(char * serverAddr, int serverPort) {
    int timeout = 15;	
    int res; 
    struct sockaddr_in addr; 
    long arg; 
    fd_set myset; 
    struct timeval tv; 
    int valopt; 
    socklen_t lon;     
    int sock;
    
    /* Get the Socket file descriptor */
    if ((sock = socket(AF_INET, SOCK_STREAM, 0)) == -1)
    {
        fprintf(stderr, "ERROR: Failed to obtain Socket Descriptor! (errno = %d)\n",errno);
        exit(-1);
    }
    
    /* Fill the socket address struct */
    addr.sin_family = AF_INET;
    addr.sin_port = htons(serverPort);
    inet_pton(AF_INET, serverAddr, &addr.sin_addr);
    memset(&(addr.sin_zero),'\0',8);

//----------------------- Timeout from http://developerweb.net/viewtopic.php?id=3196

  // Set non-blocking 
  if( (arg = fcntl(sock, F_GETFL, NULL)) < 0) { 
     fprintf(stderr, "Error fcntl(..., F_GETFL) (%s)\n", strerror(errno)); 
     exit(-1); 
  } 
  arg |= O_NONBLOCK; 
  if( fcntl(sock, F_SETFL, arg) < 0) { 
     fprintf(stderr, "Error fcntl(..., F_SETFL) (%s)\n", strerror(errno)); 
     exit(-1); 
  } 
  // Trying to connect with timeout 
  res = connect(sock, (struct sockaddr *)&addr, sizeof(addr)); 
  if (res < 0) { 
     if (errno == EINPROGRESS) { 
        fprintf(stderr, "Attempting to connect to %s on port %i...\n",serverAddr, serverPort ); 
        do { 
           tv.tv_sec = timeout; 
           tv.tv_usec = 0; 
           FD_ZERO(&myset); 
           FD_SET(sock, &myset); 
           res = select(sock+1, NULL, &myset, NULL, &tv); 
           if (res < 0 && errno != EINTR) { 
              fprintf(stderr, "Error connecting %d - %s\n", errno, strerror(errno)); 
              exit(-1); 
           } 
           else if (res > 0) { 
              // Socket selected for write 
              lon = sizeof(int); 
              if (getsockopt(sock, SOL_SOCKET, SO_ERROR, (void*)(&valopt), &lon) < 0) { 
                 fprintf(stderr, "Error in getsockopt() %d - %s\n", errno, strerror(errno)); 
                 exit(-1); 
              } 
              // Check the value returned... 
              if (valopt) { 
                 fprintf(stderr, "Error in delayed connection() %d - %s\n", valopt, strerror(valopt) 
); 
                 exit(-1); 
              } 
              break; 
           } 
           else { 
              fprintf(stderr, "Connection timed out, check that the server is running.\n"); 
              exit(-1); 
           } 
        } while (1); 
     } 
     else { 
        fprintf(stderr, "Error connecting %d - %s\n", errno, strerror(errno)); 
        exit(EXIT_FAILURE); 
     } 
  } 
  // Set to blocking mode again... 
  if( (arg = fcntl(sock, F_GETFL, NULL)) < 0) { 
     fprintf(stderr, "Error fcntl(..., F_GETFL) (%s)\n", strerror(errno)); 
     exit(-1); 
  } 
  arg &= (~O_NONBLOCK); 
  if( fcntl(sock, F_SETFL, arg) < 0) { 
     fprintf(stderr, "Error fcntl(..., F_SETFL) (%s)\n", strerror(errno)); 
     exit(-1); 
  } 


    printf("[Client] Connected to server at port %d...ok!\n", serverPort);
        return sock;
}
    


void print_usage() {
    printf("Usage:\n");
    printf("-a filename              [add or replace a file on the oldtrusty server]\n");
    printf("-c number                [provide the required circumference (length) of a circle of trust]\n");
    printf("-f filename              [fetch an existing file from the oldtrusty server]\n");
    printf("-h hostname:port         [provide the remote address hosting the server]\n");
    printf("-l                       [list all stored files and how they are protected]\n");
    printf("-n name                  [require a circle of trust to involve the named person]\n");
    printf("-u certificate           [upload a certificate to the server]\n");
    printf("-v filename certificate  [vouch for the authenticity of an existing file in the server using the indicated certificate]\n");
}



int main(int argc, char *argv[])
{
    if(argc == 1) {
    	printf("Error no arguments type -? for usage.\n");
	exit(EXIT_FAILURE);
    }
    int sockfd;
    SSL_CTX *ctx;

    char *downloads = "/Users/tom/desktop";

    char request[LENGTH];
    memset(request, '\0', LENGTH);

    enum { SEND_MODE, FETCH_MODE, VOUCH_MODE, LIST_MODE, DEFAULT_MODE } mode = DEFAULT_MODE;
    int option = 0;
    int circumference = 0, port = 0;
    char *fileName, *hostname, *trustedname, certificate;

    //Specifying the expected options
    while ((option = getopt(argc, argv,"a:c:f:h:ln:u:v:?")) != -1) {
        switch (option) {
            case 'a' : //upload a file
		snprintf(request, sizeof(request), "add");
		fileName = optarg;
                mode = SEND_MODE;
                break;
            case 'c' : //provide circumference
                circumference = atoi(optarg);
                break;
            case 'f' : //fetch a file
                snprintf(request, sizeof(request), "fetch %s",optarg);
      		fileName = optarg;
		mode = FETCH_MODE;
                break;
            case 'h' : //specify server address
                hostname = strtok(optarg, ":");
                char *temp = strtok(NULL, ":");
		if(temp != NULL) port = atoi(temp);
                break;
            case 'l' : //list all files on server
		snprintf(request, sizeof(request), "list");
		mode = LIST_MODE;
		//todo create list all files method
            case 'n' : //require name in circle
                trustedname = optarg;
            case 'u' : //upload a cert
		snprintf(request, sizeof(request), "add");
                fileName = optarg;
		mode = SEND_MODE;
                break;
            case 'v' : //vouch
                //todo create method to vouch
                snprintf(request, sizeof(request), "vouch %s",optarg);
		mode = VOUCH_MODE;
		break;
	    case '?' : //print usage
		print_usage();
		exit(EXIT_SUCCESS);
		break;
            default: print_usage();
                exit(EXIT_FAILURE);
        }
    }

    /*printf("fileName is %s\n", filepath);
    printf("hostname is %s\n", hostname);
    printf("ports is %i\n", port);
    printf("mode is %i\n", mode);*/
    
    //---------------------
   

    /* Variable Definition */
    if(hostname == NULL || port == 0) {
    	fprintf(stderr, "Please specify a hostname and port, type -? for usage\n");
	exit(EXIT_FAILURE);
    }

    if((sockfd = connectServer(hostname, port)) == -1) {
    	fprintf(stderr,"Unable to connect to the server.\n");
	exit(EXIT_FAILURE);
    }
		
    SSL *ssl = createSSL(sockfd, ctx);
    sendRequest(ssl, request); 

    int result;
    switch (mode) {
            case SEND_MODE : 
		result = sendFile(ssl, fileName);
		break;
            case FETCH_MODE : 
		 result = receiveFile(ssl, downloads);
                break;
            case LIST_MODE : 
		//todo list function
                break;
            case VOUCH_MODE : 
		//todo vouch function
                break;
            case DEFAULT_MODE: fprintf(stderr, "Please specify a send or receive argument, type -? for usage.\n");
    		exit(EXIT_FAILURE);
    }

       
    if(result == EXIT_FAILURE) {
        fprintf(stderr, "Failed to perform task.\n");
	exit(EXIT_FAILURE);
    }
    
    close(sockfd);
    SSL_CTX_free(ctx);        /* release context */
    printf("[Client] Connection lost.\n");
    
    exit(EXIT_SUCCESS);
}


