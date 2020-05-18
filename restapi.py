import flask
from flask import request, jsonify

import pandas as pd 
 
def addEvent(stringValue):# Creating the first Dataframe using dictionary 
 isThere = False
 
 # Creating the first Dataframe using dictionary 
 df1 = pd.read_csv('./bleevents.csv')
 
 try:
  variables = stringValue.split("#")
  parameters = {}
 
  if( len(variables) < 4):
   invalid = "invalid"
   return invalid
 
  dev1IME = variables[0]
  dev2IME = variables[1]
  timestamp = variables[2]
  eventType = variables[3]

  # Handle GPS data later TODO
  print(dev1IME, dev2IME, timestamp, eventType)
 
 except ValueError:
  error = "error"
  return error
 
 for index, row in df1.iterrows(): 
  if str(row['dev1IME']) == str(dev1IME) and str(row['dev2IME']) == str(dev2IME) and str(row['timestamp']) == str(timestamp) and str(row['eventType']) == str(eventType):
     isThere = True# Creating the Second Dataframe using dictionary 
 
 if isThere != True: 
 
  df2 = pd.DataFrame({"dev1IME":[dev1IME], "dev2IME":[dev2IME], "timestamp":[timestamp], "eventType":[eventType]}) 
  dff = df1.append(df2, ignore_index = True)
  dff.to_csv(r'./bleevents.csv', index = False)
 
  add = "added"
  return add
 
 else:
  update = "duplicate"
  return update

def getEvent(stringValue):# Creating the first Dataframe using dictionary 
 isThere = False
 
 # Creating the first Dataframe using dictionary 
 df1 = pd.read_csv('./bleevents.csv')
 #resp = make_response(df1.to_csv())
 #resp.headers["Content-Disposition"] = "attachment; filename=export.csv"
 #resp.headers["Content-Type"] = "text/csv"
 
 #return resp
 return flask.Response(
       df1.to_csv(),
       mimetype="text/csv",
       headers={"Content-disposition":
       "attachment; filename=filename.csv"})

def addUser(stringValue):# Creating the first Dataframe using dictionary 
 isThere = False
 
 # Creating the first Dataframe using dictionary 
 df1 = pd.read_csv('./users.csv')
 
 try:
  variables = stringValue.split("#")
  parameters = {}
 
  if( len(variables) < 0):
   invalid = "invalid"
   return invalid
 
  empno = variables[0]
  username = variables[1]
  timestamp = variables[2]

  # Handle GPS data later TODO
  print(username, empno)
 
 except ValueError:
  error = "error"
  return error
 
 for index, row in df1.iterrows(): 
  if str(row['empno']) == str(empno) and str(row['username']) == str(username):
     isThere = True# Creating the Second Dataframe using dictionary 
 
 if isThere != True: 
 
  df2 = pd.DataFrame({"empno":[empno], "username":[username], "timestamp":[timestamp]}) 
  dff = df1.append(df2, ignore_index = True)
  dff.to_csv(r'./users.csv', index = False)
 
  add = "added"
  return add
 
 else:
  update = "duplicate"
  return update

def getUser(stringValue):# Creating the first Dataframe using dictionary 
 isThere = False
 
 # Creating the first Dataframe using dictionary 
 df1 = pd.read_csv('./users.csv')
 
 try:
  variables = stringValue.split("#")
  parameters = {}
 
  if( len(variables) < 1):
   invalid = "invalid"
   return invalid
 
  empno = variables[0]

  # Handle GPS data later TODO
  print(empno)
 
 except ValueError:
  error = "error"
  return error
 
 for index, row in df1.iterrows(): 
  if str(row['empno']) == str(empno):
     user = row['username']
     return user
 
 return "error"

app = flask.Flask(__name__)
#app.config["DEBUG"] = True
application = app

@app.route("/bleevent/add",methods=['POST'])
def addingEvent():
 stringValue = request.form['value'] 
 print(stringValue)
 status = addEvent(stringValue)
 return status# Running the server in localhost:5000 

@app.route("/bleevent/get",methods=['POST'])
def getingEvent():
 stringValue = request.form['value'] 
 print(stringValue)
 status = getEvent(stringValue)
 return status # Running the server in localhost:5000 

@app.route("/user/add",methods=['POST'])
def addingUser():
 stringValue = request.form['value'] 
 print(stringValue)
 status = addUser(stringValue)
 return status# Running the server in localhost:5000 

@app.route("/user/get",methods=['POST'])
def getingUser():
 stringValue = request.form['value'] 
 print(stringValue)
 status = getUser(stringValue)
 return status# Running the server in localhost:5000 


if __name__ == '__main__':
 #app.run(ssl_context=('cert.pem','key.pem'))#debug=True, host='0.0.0.0', port=5000)
 app.run()
