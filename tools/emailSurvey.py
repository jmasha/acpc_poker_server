#!/usr/bin/python
import sys
import smtplib
import string
from email.MIMEImage import MIMEImage
from email.MIMEText import MIMEText
from email.MIMEMultipart import MIMEMultipart
from email.Utils import COMMASPACE, formatdate

#XML Stuff
from xml.dom.minidom import parse

def getEmailAddy(testKey):
    '''
    Get the email addy from the keys file
    '''
    filename = "keys/keys.xml"
    dom = parse(filename)
    username = ""
    keys = dom.getElementsByTagName("Key")
    for key in keys:
        keynode = key.getElementsByTagName("KeyValue")[0].childNodes
        for knode in keynode:
            if knode.nodeType == knode.TEXT_NODE:
                if testKey == str(knode.data).strip().upper():
                    usernode = key.getElementsByTagName("UserName")[0].childNodes
                    for unode in usernode:
                        if unode.nodeType == unode.TEXT_NODE:
                            username = str(unode.data).strip()
                        
    return username

def sendEmail(recipient,URL):
    '''
    Email the key to the submitter
    '''
    # Create the container (outer) email message.
    msg = MIMEMultipart()
    msg['Subject'] = 'Post Game Survey'
    msg['From'] = 'Poker Experiment Survey <dontemailpolaris@mailinator.com>'
    msg['To'] = recipient
    msg['Date'] = formatdate(localtime=True)
    html = '''
<html>
 <head></head>
 <body>
  <p>You have sucessfully completed the play portion of the experiment<br>
  You must now go and complete the post game survey.  Be sure to fill in all the questions<br>
  Go to the <a href="'''+URL+'''" target ="_blank">Post Game Survey</a> to finish the survey.
  <p>Note: Some webmail clients display the link above incorrectly.  If you are having trouble, the plain text link is<br>'''+URL+'''
  </p>
 </body>
</html>
    '''
    text = 'You have successfully completed the play portion.\nGo to the url '+URL+' to complete the post game survey questions.'
    msghtml = MIMEText(html,'html')
    msgtext = MIMEText(text,'text')
    msg.attach(msghtml)
    msg.attach(msgtext)
            
    # Send the email via our own SMTP server.
    s = smtplib.SMTP()
    s.connect()
    s.sendmail('dontemailpolaris@mailinator.com', recipient, msg.as_string())
    s.close()

def usage():
    print '''
    Emails a user their survey link
    Usage:  emailSurvey.py email_address URL
    '''    

def main(argv):
    recipient = getEmailAddy(argv[0])
    if not recipient == "":
        URL = argv[1]+"?key="+argv[0]
        sendEmail(recipient,URL)

if __name__ == "__main__":
    main(sys.argv[1:])
