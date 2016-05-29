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

//sends a request of size LENGTH to the server
int sendRequest(SSL *ssl, char *request) {
	char buffer[LENGTH]; //create buffer
	memset(buffer, '\0', LENGTH); //empty the buffer
	strcpy(buffer, request); //copy the request into the buffer
	if(SSL_write(ssl, buffer, sizeof(buffer)) <= 0) {
        	fprintf(stderr, "[Client] ERROR: Failed to send request. (errno = %d)\n", errno);
		return EXIT_FAILURE;
    	}
	return EXIT_SUCCESS;

}

//gets a request from the server of size LENGTH
int getServerMessage(SSL *ssl) {
    char buffer[LENGTH];
    char  *msg = malloc(LENGTH *sizeof(char));
    memset(buffer, '\0', sizeof(int));
    
    int bytesReceived = 0;
    while((bytesReceived = SSL_read(ssl, buffer, LENGTH)) > 0) {
        if (bytesReceived == LENGTH)
        {
            break;
        }
    }
    //backs up the message to msg
    strcpy(msg, buffer);
		
    //splits the string at : to check if its an error message
    char *temp = strtok(buffer, ":");
    if(strcmp(temp, "ERROR") == 0) {
	//error message so print red
	fprintf(stderr,"\x1b[31m[Server] %s\n\x1b[0m",msg);
	return EXIT_FAILURE;
    } else {
	//not an error message so print green
    	fprintf(stderr,"\x1b[32m[Server] %s\n\x1b[0m",msg);
	return EXIT_SUCCESS;
    }
}

//send the filesize and filename to the server
int sendMetaData(char *path, SSL *ssl, char **name) {
    int size = 0;
    char buffer[LENGTH];
    memset(buffer, '\0', LENGTH);
    
    //get the filesize
    struct stat st;
    stat(path, &st);
    size = st.st_size;
    //conver the int to network order
    int netsize = htonl(size);
    //get the filename
    *name = basename(path);
    
    if(SSL_write(ssl, &netsize, sizeof(netsize)) <= 0) {
        fprintf(stderr, "[Client] ERROR: Failed to send file size. (errno = %d)\n", errno);
    }
    
    
    strcpy(buffer, *name);
    if(SSL_write(ssl, buffer, sizeof(buffer)) <= 0) {
        fprintf(stderr, "[Client] ERROR: Failed to send file name. (errno = %d)\n", errno);
    }
    return size;
}

//send the circumference as int to the server
int sendCircumference(SSL *ssl, int circumference) {
    int netcircumference = htonl(circumference);    
    if(SSL_write(ssl, &netcircumference, sizeof(netcircumference)) <= 0) {
        fprintf(stderr, "[Client] ERROR: Failed to send file size. (errno = %d)\n", errno);
	return EXIT_FAILURE;
    }
    return EXIT_SUCCESS;
}

//recv the filename of a file from the server of length LENGTH
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
        return NULL;
    }
    strcpy(name, buffer);
    return name;
    
}

//receive the filesize from the server as int
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
        return 0;
    }
    //convert the int from network order
    size = ntohl(*((int*)intbuff));
    return size;
}

//send a file to the server
int sendFile(SSL *ssl, char *filePath) {
    char buffer[LENGTH];
    FILE *fs = fopen(filePath, "r");
    if(fs == NULL)
    {
        fprintf(stderr,"ERROR: File: %s not found.\n", filePath);
        return EXIT_FAILURE;
    }
    //send the preliminary metadata to the server
    char *fileName;
    int fileSize = sendMetaData(filePath, ssl, &fileName);
    if(fileName == '\0') {
        fprintf(stderr,"Failed to send file metadata\n");
        return EXIT_FAILURE;
    }
    
    fprintf(stderr,"[Client] Sending %s to the Server...\n", fileName);
    
    memset(buffer, '\0', LENGTH);
    int fs_block_sz;
    int bytesSent = 0;
    int totalSent = 0;
    int n = 5;
    //generate loading bar
    char loading[22];
    memset(loading, ' ', sizeof(loading));
    loading[0] = '[',loading[21] = ']';

    //while there are still bytes to be read from the file send them to the server
    while((fs_block_sz = fread(buffer, sizeof(char), LENGTH, fs)) > 0)
    {
	bytesSent = SSL_write(ssl, buffer, fs_block_sz);   
        if(bytesSent <= 0)
        {
            fprintf(stderr, "[Client] ERROR: Failed to send file %s. (errno = %d)\n", fileName, errno);
            return EXIT_FAILURE;
        }
	totalSent = totalSent + bytesSent;
	//calculate percentage and print loading bar progress
	float percent = ((float)totalSent/(float)fileSize)*100;
        if((int)round(percent) == n) {
		loading[n/5] = '*';
            	n = n+5;
           	fprintf(stderr,"%s\r",loading);
	        memset(buffer, '\0', LENGTH);
	}
    }
    fprintf(stderr,"[Client] File %s was sent to the server\n", fileName);
    return EXIT_SUCCESS;
}

//recieve a file from the server and output to stdout
int receiveFile(SSL *ssl) {
	
    //get the filesize anf filename
    int fileSize = recvFileSize(ssl); 	
    if(fileSize == 0) {
        fprintf(stderr, "[Client] Error receiving file metadata from server\n");
        return EXIT_FAILURE;
    }
    char *fileName = recvFileName(ssl);
    
    fprintf(stderr,"[Client] Receiving %s which is %i bytes\n",fileName,fileSize);
    free(fileName);
    
    char buffer[LENGTH];
    memset(buffer, '\0', LENGTH);
    int bytesReceived = 0;
    int totalWritten = 0;
    int remaining = fileSize;
    int n = 5;
    //generate loading bar
    char loading[22];
    memset(loading, ' ', sizeof(loading));
    loading[0] = '[',loading[21] = ']';
    
    //while there are still bytes remaining read from socket
    while((bytesReceived = SSL_read(ssl, buffer, MIN(LENGTH, remaining))) > 0)
    {
	//write those bytes to stdout
        int bytesWritten = fwrite(buffer, sizeof(char), bytesReceived, stdout);
        if(bytesWritten < bytesReceived)
        {
            error("File write failed.\n");
        }
        memset(buffer, '\0', LENGTH);
        totalWritten = totalWritten + bytesWritten;
        remaining = remaining - bytesWritten;
	//calculate percentage and print loading bar progress
	float percent = ((float)totalWritten/(float)fileSize)*100;
        if((int)round(percent) == n) {
		loading[n/5] = '*';
            	n = n+5;
           	fprintf(stderr,"%s\r",loading);
	        memset(buffer, '\0', LENGTH);
	}
        
    }
    fprintf(stderr,"\n");
    if(totalWritten < fileSize)
    {
	   fprintf(stderr,"File transfer was unsuccessful.\n");
	   return EXIT_FAILURE;
    }
    fprintf(stderr,"File transfer complete.\n");
    return EXIT_SUCCESS;
}

SSL_CTX* InitCTX(void)
{   const SSL_METHOD *method;
    SSL_CTX *ctx;

    OpenSSL_add_all_algorithms();  /* Load cryptos, et.al. */
    SSL_load_error_strings();   /* Bring in and register error messages */
    method = SSLv23_client_method();  /* Create new client-method instance */
    ctx = SSL_CTX_new(method);   /* Create new context */
    if ( ctx == NULL )
    {
        fprintf(stderr, "Unable to create a new SSL context structure.\n");
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
        fprintf(stderr,"Server certificates:\n");
        line = X509_NAME_oneline(X509_get_subject_name(cert), 0, 0);
        fprintf(stderr,"Subject: %s\n", line);
        free(line);       /* free the malloc'ed string */
        line = X509_NAME_oneline(X509_get_issuer_name(cert), 0, 0);
        fprintf(stderr,"Issuer: %s\n", line);
        free(line);       /* free the malloc'ed string */
        X509_free(cert);     /* free the malloc'ed certificate copy */
    }
    else
        fprintf(stderr,"No certificates.\n");
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
        fprintf(stderr,"\x1b[33mConnected with %s encryption\n\x1b[0m", SSL_get_cipher(ssl));
	return ssl;
    }   

}

//creates a SSL connection between the client and server
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
        fprintf(stderr, "[Client] ERROR: Failed to obtain Socket Descriptor! (errno = %d)\n",errno);
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
     fprintf(stderr, "[Client] Error fcntl(..., F_GETFL) (%s)\n", strerror(errno)); 
     exit(-1); 
  } 
  arg |= O_NONBLOCK; 
  if( fcntl(sock, F_SETFL, arg) < 0) { 
     fprintf(stderr, "[Client] Error fcntl(..., F_SETFL) (%s)\n", strerror(errno)); 
     exit(-1); 
  } 
  // Trying to connect with timeout 
  res = connect(sock, (struct sockaddr *)&addr, sizeof(addr)); 
  if (res < 0) { 
     if (errno == EINPROGRESS) { 
        fprintf(stderr, "[Client] Attempting to connect to %s on port %i...\n",serverAddr, serverPort ); 
        do { 
           tv.tv_sec = timeout; 
           tv.tv_usec = 0; 
           FD_ZERO(&myset); 
           FD_SET(sock, &myset); 
           res = select(sock+1, NULL, &myset, NULL, &tv); 
           if (res < 0 && errno != EINTR) { 
              fprintf(stderr, "[Client] Error connecting %d - %s\n", errno, strerror(errno)); 
              exit(-1); 
           } 
           else if (res > 0) { 
              // Socket selected for write 
              lon = sizeof(int); 
              if (getsockopt(sock, SOL_SOCKET, SO_ERROR, (void*)(&valopt), &lon) < 0) { 
                 fprintf(stderr, "[Client] Error in getsockopt() %d - %s\n", errno, strerror(errno)); 
                 exit(-1); 
              } 
              // Check the value returned... 
              if (valopt) { 
                 fprintf(stderr, "[Client] Error in delayed connection() %d - %s\n", valopt, strerror(valopt)); 
                 exit(-1); 
              } 
              break; 
           } 
           else { 
              fprintf(stderr, "[Client] Connection timed out, check that the server is running.\n"); 
              exit(-1); 
           } 
        } while (1); 
     } 
     else { 
        fprintf(stderr, "[Client] Error connecting %d - %s\n", errno, strerror(errno)); 
        exit(EXIT_FAILURE); 
     } 
  } 
  // Set to blocking mode again... 
  if( (arg = fcntl(sock, F_GETFL, NULL)) < 0) { 
     fprintf(stderr, "[Client] Error fcntl(..., F_GETFL) (%s)\n", strerror(errno)); 
     exit(-1); 
  } 
  arg &= (~O_NONBLOCK); 
  if( fcntl(sock, F_SETFL, arg) < 0) { 
     fprintf(stderr, "[Client] Error fcntl(..., F_SETFL) (%s)\n", strerror(errno)); 
     exit(-1); 
  } 


    fprintf(stderr,"[Client] Connected to server at port %d...ok!\n", serverPort);
        return sock;
}
    


void print_usage() {
    fprintf(stderr,"Usage:\n");
    fprintf(stderr,"-a filename              [add or replace a file on the oldtrusty server]\n");
    fprintf(stderr,"-c number                [provide the required circumference (length) of a circle of trust]\n");
    fprintf(stderr,"-f filename              [fetch an existing file from the oldtrusty server]\n");
    fprintf(stderr,"-h hostname:port         [provide the remote address hosting the server]\n");
    fprintf(stderr,"-l                       [list all stored files and how they are protected]\n");
    fprintf(stderr,"-n name                  [require a circle of trust to involve the named person]\n");
    fprintf(stderr,"-u certificate           [upload a certificate to the server]\n");
    fprintf(stderr,"-v filename certificate  [vouch for the authenticity of an existing file in the server using the indicated certificate]\n");
}



int main(int argc, char *argv[])
{
    if(argc == 1) {
    	fprintf(stderr,"Error no arguments type -? for usage.\n");
	exit(EXIT_FAILURE);
    }
    int sockfd;
    SSL_CTX *ctx;

    //setup variables that define what the client does
    char request[LENGTH];
    memset(request, '\0', LENGTH);

    enum { SEND_MODE, FETCH_MODE, VOUCH_MODE, LIST_MODE, DEFAULT_MODE } mode = DEFAULT_MODE;
    int option = 0;
    int circumference = 0, port = 0;
    char *fileName, *hostname, *trustedname, *certificate, *certname, *msg;

    //Specifying the expected options
    while ((option = getopt(argc, argv,"a:c:f:h:ln:u:v:?")) != -1) {
        switch (option) {
            case 'a' : //upload a file
		fileName = optarg;
		//print the request to a variable that will be send later
		snprintf(request, sizeof(request), "add file %s", basename(fileName));
		//set the mode of the client
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
		//get the hostname and port
                hostname = strtok(optarg, ":");
                char *temp = strtok(NULL, ":");
		if(temp != NULL) port = atoi(temp);
                break;
            case 'l' : //list all files on server
		snprintf(request, sizeof(request), "list");
		mode = LIST_MODE;
		break;
            case 'n' : //require name in circle
                trustedname = optarg;
		break;
            case 'u' : //upload a cert
                fileName = optarg;
		certname = basename(fileName);
		snprintf(request, sizeof(request), "add cert %s",certname);		
		mode = SEND_MODE;
                break;
            case 'v' : //vouch
		if(argv[optind] == NULL || argv[optind][0] == '-') { //check whether certificate arg is given
			fprintf(stderr,"Please specify a certicate. Type -? for usage.\n");
			exit(EXIT_FAILURE);
		}
		fileName = argv[optind];
		certname = basename(fileName);
		snprintf(request, sizeof(request), "vouch %s %s",optarg, certname);
		mode = VOUCH_MODE;
		optind = optind+1;
		break;
	    case '?' : //print usage
		print_usage();
		exit(EXIT_SUCCESS);
		break;
            default: print_usage();
                exit(EXIT_FAILURE);
        }
}

    //no hostname specified
    if(hostname == NULL || port == 0) {
    	fprintf(stderr, "[Client] Please specify a hostname and port, type -? for usage\n");
	exit(EXIT_FAILURE);
    }
    //try and connect to the server
    if((sockfd = connectServer(hostname, port)) == -1) {
    	fprintf(stderr, "[Client] Unable to connect to the server.\n");
	exit(EXIT_FAILURE);
    }
    //SSL handshake		
    SSL *ssl = createSSL(sockfd, ctx);
    
    //ask the server to server us
    sendRequest(ssl, request); 
    int getMsg = 1; //whether or not we want to get a message from the server after all is said and done

    int result;
    //execute the requested client options
    switch (mode) {
            case SEND_MODE : 
		result = sendFile(ssl, fileName); 
		if(result == EXIT_FAILURE) {
			exit(EXIT_FAILURE);
		}
		break;

            case FETCH_MODE :
		//first send circumference then send trustedname
		sendCircumference(ssl, circumference);
		memset(request, '\0', LENGTH); //empty the request buffer, ready to send trustedname
		snprintf(request, sizeof(request), "%s",trustedname);	
		sendRequest(ssl, request); //send the trustedname as a request (only doing this because I cant be bothered writing a new method and this one works fine.
		if(getServerMessage(ssl) == EXIT_FAILURE) {
			exit(EXIT_FAILURE);
		}		
		result = receiveFile(ssl);
                break;

            case LIST_MODE : 
		result = receiveFile(ssl);
		break;

            case VOUCH_MODE : 		
		if(getServerMessage(ssl) == EXIT_FAILURE) {
			getMsg = 0; 
			break;
		}
		result = sendFile(ssl, fileName);//send the vouching cert to the server
		if(result == EXIT_FAILURE) exit(EXIT_FAILURE);
		break;
            case DEFAULT_MODE: fprintf(stderr, "[Client] Please specify a send or receive argument, type -? for usage.\n");
    		exit(EXIT_FAILURE);
    }
    //get the result of our request back from the server
    if(getMsg) getServerMessage(ssl);
    
       
    close(sockfd);
    SSL_CTX_free(ctx);        /* release context */
    fprintf(stderr,"[Client] Connection lost.\n");
    
    exit(EXIT_SUCCESS);
}


