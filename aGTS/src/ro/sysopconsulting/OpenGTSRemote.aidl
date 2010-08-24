package ro.sysopconsulting;


interface OpenGTSRemote {

	int loggingState();
    void startLogging();
    void pauseLogging();
    void resumeLogging();
	void stopLogging();

}