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
   invalid = “invalid”
   return invalid
 
  dev1IME = variables[0]
  dev2IME = variables[1]
  timestamp = variables[2]
  eventType = variables[3]

  # Handle GPS data later TODO
  print(dev1IME, dev2IME, timestamp, eventType)
 
 except ValueError:
  error = “error”
  return error
 
 for index, row in df1.iterrows(): 
  if str(row[‘dev1IME’]) == str(dev1IME) and str(row[‘dev2IME’]) == str(dev2IME) and str(row['timestamp']) == str(timestamp) and str(row['eventType']) == str(eventType):
     row[‘rating’] = rating
     isThere = True# Creating the Second Dataframe using dictionary 
 
 if isThere != True: 
 
  df2 = pd.DataFrame({“dev1IME”:[dev1IME], “dev2IME”:[dev2IME], “timestamp”:[timestamp], "eventType":[eventType]}) 
  dff = df1.append(df2, ignore_index = True)
  dff.to_csv(r'./rating.csv', index = False)
 
  add = “added”
  return add
 
 else:
  update = "duplicate"
  return update

app = Flask(__name__)@app.route(“/bleevent/add”,methods=[‘POST’])
def addRating():
 stringValue = request.form[‘value’] 
 print(stringValue)
 status = addEvent(stringValue)
 
 return status# Running the server in localhost:5000 
if __name__ == ‘__main__’:
 app.run(debug=True, host=’0.0.0.0', port=5000)
