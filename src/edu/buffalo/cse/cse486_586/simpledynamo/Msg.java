package edu.buffalo.cse.cse486_586.simpledynamo;

import java.io.Serializable;


/**
 * @author forkloop
 *
 */

class JoinMsg implements Serializable {

	/**
	 * 
	 */
	private static final long serialVersionUID = -7143399440835820120L;
	int sender;
	
}


class InsertMsg implements Serializable {

	/**
	 * 
	 */
	private static final long serialVersionUID = -1021641386366496103L;
	
	String key;
	String value;
	int owner;
	int sender;
}


class InquiryMsg implements Serializable {
	
	/**
	 * 
	 */
	private static final long serialVersionUID = -5399839778303358947L;
	String key;
	int sender;
	int owner;
}



class ReplicateMsg implements Serializable {
	
	/**
	 * 
	 */
	private static final long serialVersionUID = -8793533268709650705L;
	String key;
	String value;
	int owner;
	int sender;
	int asker;
	char type;
}


class QuorumMsg implements Serializable {
	
	/**
	 * 
	 */
	private static final long serialVersionUID = 2905464854406223348L;
	String key;
	char type;		/* g or p */
	int owner;
	int sender;
	int asker;
	
}

class RecoveryMsg implements Serializable {

	String rec;
}

class ConfirmMsg implements Serializable {

	/**
	 * 
	 */
	private static final long serialVersionUID = 7105857416483104284L;
	String key;
	int owner;
}


class AckMsg implements Serializable {
	
	/**
	 * 
	 */
	private static final long serialVersionUID = -5072522664179893523L;
	String key;
	String value;
}